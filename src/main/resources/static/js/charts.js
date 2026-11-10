/* Dashboard and analytics charts (Chart.js). Amounts are Taka, shown with Bangladeshi grouping. */
const chequeCharts = (function () {
    const IN = '#198754', OUT = '#dc3545';

    // 125000.5 -> "1,25,000.50"
    function taka(v) {
        const [whole, frac] = Math.abs(Number(v)).toFixed(2).split('.');
        const last3 = whole.slice(-3), rest = whole.slice(0, -3);
        const grouped = (rest ? rest.replace(/\B(?=(\d{2})+(?!\d))/g, ',') + ',' : '') + last3;
        return (v < 0 ? '-' : '') + grouped + '.' + frac;
    }

    // Axis ticks: 1,25,000 -> "1.3 L", 2,00,00,000 -> "2 Cr"
    function short(v) {
        const a = Math.abs(v);
        if (a >= 1e7) return (v / 1e7).toFixed(a >= 1e8 ? 0 : 1) + ' Cr';
        if (a >= 1e5) return (v / 1e5).toFixed(a >= 1e6 ? 0 : 1) + ' L';
        if (a >= 1e3) return (v / 1e3).toFixed(0) + 'k';
        return String(v);
    }

    const money = {
        y: {beginAtZero: true, ticks: {callback: short}},
        x: {grid: {display: false}}
    };
    const tooltip = {callbacks: {label: c => (c.dataset.label ? c.dataset.label + ': ' : '') + 'Tk ' + taka(c.parsed.y ?? c.parsed.x)}};

    function inOut(id, labels, a, b, aLabel, bLabel) {
        const el = document.getElementById(id);
        if (!el) return;
        new Chart(el, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [
                    {label: aLabel, data: a, backgroundColor: IN, borderRadius: 3, maxBarThickness: 32},
                    {label: bLabel, data: b, backgroundColor: OUT, borderRadius: 3, maxBarThickness: 32}
                ]
            },
            options: {maintainAspectRatio: false, scales: money, plugins: {tooltip: tooltip, legend: {position: 'bottom'}}}
        });
    }

    function ranking(id, names, amounts, colour) {
        const el = document.getElementById(id);
        if (!el) return;
        if (!names.length) {
            el.replaceWith(Object.assign(document.createElement('p'), {className: 'text-secondary small mb-0', textContent: 'Nothing yet.'}));
            return;
        }
        new Chart(el, {
            type: 'bar',
            data: {labels: names, datasets: [{data: amounts, backgroundColor: colour, borderRadius: 3, maxBarThickness: 26}]},
            options: {
                indexAxis: 'y', maintainAspectRatio: false,
                scales: {x: {beginAtZero: true, ticks: {callback: short}}, y: {grid: {display: false}}},
                plugins: {legend: {display: false}, tooltip: tooltip}
            }
        });
    }

    return {inOut: inOut, ranking: ranking};
})();
