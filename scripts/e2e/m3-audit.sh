#!/usr/bin/env bash
# M3 acceptance test: audit log, status history, append-only tables.
# Needs the app running (dev profile), admin password Cheque#2026, and
# scripts/e2e/m2-authorization.sh run first (it creates the test users).
# Usage: bash scripts/e2e/m3-audit.sh
B=http://localhost:8080
W=$(mktemp -d)
DB() { PGPASSWORD=chequetrack psql -w -U chequetrack -h localhost -d chequetrack -tAc "$1" 2>&1; }
csrf() { grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//'; }
login() { local t; t=$(curl -s -c "$W/$1" -b "$W/$1" $B/login | csrf)
  curl -s -c "$W/$1" -b "$W/$1" -o /dev/null -w '%{redirect_url}' --data-urlencode "_csrf=$t" \
       --data-urlencode "username=$2" --data-urlencode "password=$3" $B/login; }
get() { curl -s -b "$W/$1" -c "$W/$1" "$B$2"; }
code() { curl -s -b "$W/$1" -c "$W/$1" -o /dev/null -w '%{http_code}' "$B$2"; }
post() { local jar=$1 path=$2; shift 2; local t r; t=$(get "$jar" / | csrf)
  r=$(curl -s -b "$W/$jar" -c "$W/$jar" -o "$W/last.html" -w '%{http_code} %{redirect_url}' --data-urlencode "_csrf=$t" "$@" "$B$path")
  case "$r" in "302 http"*) curl -s -b "$W/$jar" -c "$W/$jar" -o "$W/last.html" "${r#302 }";; esac
  echo "$r"; }
check() { if [ "$2" = "$3" ]; then echo "  PASS  $1"; else echo "  FAIL  $1 (expected '$3', got '$2')"; fi; }
contains() { if echo "$2" | grep -q -- "$3"; then echo "  PASS  $1"; else echo "  FAIL  $1 (missing '$3')"; fi; }
absent() { if echo "$2" | grep -q -- "$3"; then echo "  FAIL  $1 (found '$3')"; else echo "  PASS  $1"; fi; }
count() { DB "select count(*) from audit_log where $1"; }

ID() { DB "select id from app_user where username='$1'"; }
C1=$(ID cashier1); M1=$(ID manager1); A1=$(ID auditor1)
[ -z "$M1" ] && { echo "Run scripts/e2e/m2-authorization.sh first"; exit 1; }

echo "== Login events"
before=$(count "action='LOGIN_FAILED' and username='admin'")
login x admin 'wrong-password' >/dev/null
check "wrong password logged" "$(count "action='LOGIN_FAILED' and username='admin'")" "$((before + 1))"
login x nosuchuser 'whatever' >/dev/null
check "unknown username logged (no company)" "$(DB "select summary||'|'||coalesce(company_id::text,'null') from audit_log where username='nosuchuser' order by id desc limit 1")" "Unknown username (web)|null"
before=$(count "action='LOGIN_SUCCESS' and username='admin'")
check "admin login" "$(login admin admin 'Cheque#2026')" "$B/"
check "successful login logged" "$(count "action='LOGIN_SUCCESS' and username='admin'")" "$((before + 1))"
check "login logged with IP address" "$(DB "select ip_address is not null from audit_log where action='LOGIN_SUCCESS' and username='admin' order by id desc limit 1")" t

echo "== Lockout"
post admin /admin/users/$A1/unlock >/dev/null
for i in 1 2 3 4 5; do login lock auditor1 'bad-password' >/dev/null; done
check "lock logged" "$(DB "select summary from audit_log where action='ACCOUNT_LOCKED' and entity_id='$A1' order by id desc limit 1")" "Account 'auditor1' locked for 15 minutes after 5 failed logins"
login lock auditor1 'bad-password' >/dev/null
check "attempt while locked logged as refused" "$(DB "select summary from audit_log where username='auditor1' order by id desc limit 1")" "Login refused: account locked (web)"
post admin /admin/users/$A1/unlock >/dev/null
check "unlock logged" "$(DB "select action from audit_log where entity_type='User' and entity_id='$A1' order by id desc limit 1")" "USER_UNLOCKED"

echo "== Changes record who, what and old -> new values"
check "branch creation logged by admin" "$(DB "select username from audit_log where action='BRANCH_CREATED' and summary like '%CTG%' order by id desc limit 1")" "admin"
V=$(DB "select version from company where code='MAIN'")
post admin /admin/company -d version=$V -d name='Acme Traders Ltd' -d phone='01700000000' >/dev/null
check "company update details" "$(DB "select details->'name'->>'to' from audit_log where action='COMPANY_UPDATED' order by id desc limit 1")" "Acme Traders Ltd"
n=$(count "action='COMPANY_UPDATED'"); post admin /admin/company -d version=$(DB "select version from company where code='MAIN'") -d name='Acme Traders Ltd' -d phone='01700000000' >/dev/null
check "no-op save not logged" "$(count "action='COMPANY_UPDATED'")" "$n"
check "role permission change shows before/after" "$(DB "select details->'permissions'->>'to' from audit_log where action='ROLE_UPDATED' order by id desc limit 1")" '["BRANCH_VIEW", "USER_VIEW"]'
check "user creation logged with role name" "$(DB "select details->'role'->>'to' from audit_log where action='USER_CREATED' and entity_id='$C1'")" "Cashier"
check "password reset logged, password not stored" "$(DB "select count(*) from audit_log where action='PASSWORD_RESET' and entity_id='$C1' and details is null")" "$(DB "select count(*) from audit_log where action='PASSWORD_RESET' and entity_id='$C1'")"
check "first-login password change logged" "$(DB "select username from audit_log where action='PASSWORD_CHANGED' and entity_id='$C1' order by id desc limit 1")" "cashier1"
check "session end of disabled user logged" "$(DB "select count(*) > 0 from audit_log where action='SESSION_ENDED' and entity_id='$M1'")" "t"

echo "== Failed actions leave no trace"
n=$(count "action='BRANCH_CREATED'")
post admin /admin/branches -d code=HO -d name=Duplicate >/dev/null
check "rejected duplicate branch not logged" "$(count "action='BRANCH_CREATED'")" "$n"

echo "== Status history with reasons"
post admin /admin/users/$C1/disable >/dev/null
contains "disable without reason refused" "$(cat $W/last.html)" "Enter the reason for disabling"
post admin /admin/users/$C1/disable -d reason='On leave until December' >/dev/null
post admin /admin/users/$C1/enable -d reason='Back from leave' >/dev/null
check "first status row is creation" "$(DB "select coalesce(from_status,'-')||'>'||to_status||':'||reason from status_history where entity_type='User' and entity_id=$C1 order by id limit 1")" "->ACTIVE:User created"
check "latest status rows have reasons" "$(DB "select string_agg(s, ' | ' order by id) from (select id, from_status||'>'||to_status||':'||reason s from status_history where entity_type='User' and entity_id=$C1 order by id desc limit 2) t")" "ACTIVE>DISABLED:On leave until December | DISABLED>ACTIVE:Back from leave"
H=$(get admin /admin/users/$C1/history)
contains "history page shows reason" "$H" "On leave until December"
contains "history page shows audit entries" "$H" "USER_DISABLED"
contains "history page shows changed fields" "$H" "audit-changes"

echo "== Audit log screen"
L=$(get admin /audit)
contains "audit log lists entries" "$L" "LOGIN_SUCCESS"
contains "audit log has paging footer" "$L" " of "
F=$(get admin "/audit?action=USER_DISABLED")
contains "filter by action" "$F" "audit-action[^>]*>USER_DISABLED"
absent "filter excludes other actions" "$F" "audit-action[^>]*>LOGIN_SUCCESS"
F=$(get admin "/audit?username=cashier1")
absent "filter by user excludes admin rows" "$F" '<td class="small">admin</td>'
check "date filter in the future is empty" "$(get admin "/audit?from=2099-01-01" | grep -c 'No entries.')" 1
check "LIKE wildcards are escaped" "$(get admin "/audit?username=%25" | grep -c 'No entries.')" 1

echo "== Who may see it"
login mgr manager1 'Manager#2026' >/dev/null
check "manager (no AUDIT_VIEW) /audit -> 403" "$(code mgr /audit)" 403
absent "manager has no Audit menu" "$(get mgr /)" "/audit"
check "manager sees history of own-branch user" "$(code mgr /admin/users/$M1/history)" 200
check "manager history of CTG user -> 404" "$(code mgr /admin/users/$C1/history)" 404

echo "== Logout"
t=$(get mgr / | csrf); curl -s -b "$W/mgr" -c "$W/mgr" -o /dev/null --data-urlencode "_csrf=$t" $B/logout
check "logout logged" "$(DB "select action from audit_log where username='manager1' order by id desc limit 1")" LOGOUT

echo "== Append-only at database level"
contains "UPDATE audit_log refused" "$(DB "update audit_log set summary='x' where id=(select max(id) from audit_log)")" "append-only"
contains "DELETE audit_log refused" "$(DB "delete from audit_log")" "append-only"
contains "TRUNCATE status_history refused" "$(DB "truncate status_history")" "append-only"
contains "DELETE status_history refused" "$(DB "delete from status_history")" "append-only"
rm -rf "$W"
