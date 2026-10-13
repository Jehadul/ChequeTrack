#!/usr/bin/env bash
# M2 acceptance test: roles, permissions, branch access, session refresh.
# Needs the app running (dev profile) and the admin password set to Cheque#2026.
# Creates/deletes test data (branches CTG, DHK; users cashier1, manager1, auditor1).
# Usage: bash scripts/e2e/m2-authorization.sh
B=http://localhost:8080
W=$(mktemp -d)
DB() { PGPASSWORD=chequetrack psql -w -U chequetrack -h localhost -d chequetrack -tAc "$1"; }
csrf() { grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//'; }
# login <jar> <user> <password>  -> prints redirect target
login() { local t; t=$(curl -s -c "$W/$1" -b "$W/$1" $B/login | csrf)
  curl -s -c "$W/$1" -b "$W/$1" -o /dev/null -w '%{redirect_url}' --data-urlencode "_csrf=$t" \
       --data-urlencode "username=$2" --data-urlencode "password=$3" $B/login; }
get() { curl -s -b "$W/$1" -c "$W/$1" "$B$2"; }
code() { curl -s -b "$W/$1" -c "$W/$1" -o /dev/null -w '%{http_code}' "$B$2"; }
# post <jar> <path> <data...>  -> "status redirect"
post() { local jar=$1 path=$2; shift 2; local t r; t=$(get "$jar" / | csrf)
  r=$(curl -s -b "$W/$jar" -c "$W/$jar" -o "$W/last.html" -w '%{http_code} %{redirect_url}' --data-urlencode "_csrf=$t" "$@" "$B$path")
  # Like a browser, open the page we were redirected to (this shows and consumes flash messages)
  case "$r" in "302 http"*) curl -s -b "$W/$jar" -c "$W/$jar" -o "$W/last.html" "${r#302 }";; esac
  echo "$r"; }
check() { if [ "$2" = "$3" ]; then echo "  PASS  $1"; else echo "  FAIL  $1 (expected '$3', got '$2')"; fi; }
contains() { if echo "$2" | grep -q "$3"; then echo "  PASS  $1"; else echo "  FAIL  $1 (missing '$3')"; fi; }
absent() { if echo "$2" | grep -q "$3"; then echo "  FAIL  $1 (found '$3')"; else echo "  PASS  $1"; fi; }
first_change() { # first-login password change for a fresh user
  local t; t=$(get "$1" /account/change-password | csrf)
  curl -s -b "$W/$1" -c "$W/$1" -o /dev/null --data-urlencode "_csrf=$t" --data-urlencode "currentPassword=$2" \
     --data-urlencode "newPassword=$3" --data-urlencode "confirmPassword=$3" $B/account/change-password; }

# Clean previous run's test data so the script can be repeated
DB "delete from user_branch where user_id in (select id from app_user where username in ('cashier1','manager1','auditor1'));
    delete from password_history where user_id in (select id from app_user where username in ('cashier1','manager1','auditor1'));
    delete from app_user where username in ('cashier1','manager1','auditor1');
    delete from role_permission where role_id in (select id from role where code='TESTROLE'); delete from role where code='TESTROLE';
    delete from branch where code in ('CTG','DHK');
    insert into role_permission select r.id, 'USER_VIEW' from role r where r.code='ACCOUNTS_MANAGER' on conflict do nothing;" >/dev/null

echo "== Admin"
check "admin login" "$(login admin admin 'Cheque#2026')" "$B/"
H=$(get admin /)
contains "sidebar shows Administration" "$H" "Administration"
contains "user menu shows role" "$H" "Administrator"
for p in /admin/company /admin/branches /admin/roles /admin/users /admin/branches/new /admin/roles/new /admin/users/new; do
  check "GET $p" "$(code admin $p)" 200; done
check "create branch CTG" "$(post admin /admin/branches -d code=ctg -d name=Chattogram -d email=ctg@example.com)" "302 $B/admin/branches"
check "create branch DHK" "$(post admin /admin/branches -d code=DHK -d name='Dhaka Gulshan')" "302 $B/admin/branches"
post admin /admin/branches -d code=HO -d name=Dup >/dev/null; contains "duplicate branch code refused" "$(cat $W/last.html)" "already exists"
post admin /admin/branches -d code='bad code!' -d name=X >/dev/null; contains "invalid branch code refused" "$(cat $W/last.html)" "Use up to 20 letters"
CTG=$(DB "select id from branch where code='CTG'"); DHK=$(DB "select id from branch where code='DHK'"); HO=$(DB "select id from branch where code='HO'")
R() { DB "select id from role where code='$1'"; }
mk() { post admin /admin/users -d username=$1 -d fullName="$2" -d roleId=$(R $3) -d homeBranchId=$4 "${@:5}" >/dev/null
       DB "select id from app_user where username='$1'"; }
C1=$(mk cashier1 'Cashier One' CASHIER $CTG); M1=$(mk manager1 'Manager One' ACCOUNTS_MANAGER $DHK); A1=$(mk auditor1 'Auditor One' AUDITOR $HO -d allBranches=true)
check "3 users created" "$(DB "select count(*) from app_user where username in ('cashier1','manager1','auditor1')")" 3
post admin /admin/users -d username=cashier1 -d fullName=Dup -d roleId=$(R CASHIER) -d homeBranchId=$CTG >/dev/null
contains "duplicate username refused" "$(cat $W/last.html)" "already taken"
TP=$(post admin /admin/users/$C1/reset-password >/dev/null; cat $W/last.html | grep -o '<code class="fs-5[^>]*>[^<]*' | sed 's/.*>//')
check "reset shows temporary password" "$([ -n "$TP" ] && echo yes)" yes
TPM=$(post admin /admin/users/$M1/reset-password >/dev/null; cat $W/last.html | grep -o '<code class="fs-5[^>]*>[^<]*' | sed 's/.*>//')
ADMIN_ID=$(DB "select id from app_user where username='admin'")
post admin /admin/users/$ADMIN_ID/disable -d reason=test >/dev/null; contains "admin cannot disable self" "$(cat $W/last.html)" "cannot disable your own"
post admin /admin/users/$ADMIN_ID -d version=$(DB "select version from app_user where id=$ADMIN_ID") -d fullName='System Administrator' -d roleId=$(R CASHIER) -d homeBranchId=$HO -d allBranches=true >/dev/null
contains "admin cannot change own role" "$(cat $W/last.html)" "cannot change your own role"
ADMINROLE=$(R ADMIN)
post admin /admin/roles/$ADMINROLE -d version=$(DB "select version from role where id=$ADMINROLE") -d code=ADMIN -d name=Administrator >/dev/null
check "ADMIN role keeps all permissions" "$(DB "select count(*) from role_permission where role_id=$ADMINROLE")" "$(DB 'select count(*) from permission')"
post admin /admin/users/$C1 -d version=0 -d fullName=Stale -d roleId=$(R CASHIER) -d homeBranchId=$CTG >/dev/null
contains "stale form version refused (optimistic lock)" "$(cat $W/last.html)" "changed by another user"

echo "== Accounts manager (home DHK)"
check "manager first login" "$(login mgr manager1 "$TPM")" "$B/"
check "forced to change password" "$(curl -s -b $W/mgr -o /dev/null -w '%{redirect_url}' $B/)" "$B/account/change-password"
first_change mgr "$TPM" 'Manager#2026'
H=$(get mgr /)
contains "manager sees Users menu" "$H" "/admin/users"
absent "manager has no Roles menu" "$H" "/admin/roles"
absent "manager has no Company menu" "$H" "/admin/company"
L=$(get mgr /admin/users)
contains "manager sees own-branch user" "$L" "manager1"
absent "manager does not see CTG cashier" "$L" "cashier1"
check "manager opens CTG user by URL -> 404" "$(code mgr /admin/users/$C1)" 404
check "manager opens branch edit page -> 403" "$(code mgr /admin/branches/$CTG/edit)" 403
check "manager /admin/roles -> 403" "$(code mgr /admin/roles)" 403
check "manager /admin/company -> 403" "$(code mgr /admin/company)" 403
check "manager /admin/users/new -> 403" "$(code mgr /admin/users/new)" 403
check "manager POST disable user -> 403" "$( post mgr /admin/users/$M1/disable -d reason=x | cut -d' ' -f1)" 403
contains "manager branch list shows DHK" "$(get mgr /admin/branches)" "Dhaka Gulshan"
absent "manager branch list hides CTG" "$(get mgr /admin/branches)" "Chattogram"

echo "== Cashier (home CTG)"
login cas cashier1 "$TP" >/dev/null; first_change cas "$TP" 'Cashier#2026'
H=$(get cas /)
absent "cashier has no Users menu" "$H" "/admin/users"
check "cashier /admin/users -> 403" "$(code cas /admin/users)" 403
contains "cashier sees own branch" "$(get cas /admin/branches)" "Chattogram"
absent "cashier does not see HO" "$(get cas /admin/branches)" "Head Office"

echo "== Changes apply to open sessions immediately"
MGRROLE=$(R ACCOUNTS_MANAGER)
post admin /admin/roles/$MGRROLE -d version=$(DB "select version from role where id=$MGRROLE") -d code=ACCOUNTS_MANAGER -d name='Accounts Manager' -d permissions=BRANCH_VIEW >/dev/null
check "manager loses Users page on next click" "$(code mgr /admin/users)" 403
post admin /admin/roles/$MGRROLE -d version=$(DB "select version from role where id=$MGRROLE") -d code=ACCOUNTS_MANAGER -d name='Accounts Manager' -d permissions=BRANCH_VIEW -d permissions=USER_VIEW >/dev/null
check "manager gets it back on next click" "$(code mgr /admin/users)" 200
post admin /admin/users/$M1/disable -d reason="E2E test: disabled" >/dev/null
check "disabled manager is logged out on next click" "$(curl -s -b $W/mgr -o /dev/null -w '%{redirect_url}' $B/)" "$B/login?error=disabled"
check "disabled manager cannot log in" "$(login mgr2 manager1 'Manager#2026')" "$B/login?error=disabled"
post admin /admin/users/$M1/enable >/dev/null

echo "== Roles"
check "create custom role" "$(post admin /admin/roles -d code=testrole -d name='Test Role' -d permissions=BRANCH_VIEW -d permissions=NOT_A_PERMISSION | cut -d' ' -f1)" 302
check "unknown permission ignored" "$(DB "select string_agg(permission_code, ',') from role_permission where role_id=(select id from role where code='TESTROLE')")" "BRANCH_VIEW"
post admin /admin/roles/$(R CASHIER)/delete >/dev/null; contains "built-in role cannot be deleted" "$(cat $W/last.html)" "cannot be deleted"
check "delete unused custom role" "$(post admin /admin/roles/$(R TESTROLE)/delete | cut -d' ' -f1)" 302

echo "== Branch deactivation rule"
post admin /admin/branches/$CTG/deactivate >/dev/null; contains "cannot deactivate home branch of active users" "$(cat $W/last.html)" "home branch of 1 active user"

echo "== API"
T=$(curl -s -X POST -H 'Content-Type: application/json' -d '{"username":"auditor1","password":"x"}' $B/api/auth/login | grep -o '"status":[0-9]*')
check "API login still guarded" "$T" '"status":401'
rm -rf "$W"
