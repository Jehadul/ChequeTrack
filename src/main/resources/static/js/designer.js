/*
 * Cheque layout designer: fields on a leaf drawn to scale (data-scale pixels per mm).
 * Dragging or resizing a field saves its position at once and updates the table of exact values;
 * typing in the table moves the field on screen (saved with "Save field settings").
 */
$(function () {
    const $sheet = $('#sheet');
    if (!$sheet.length) return;
    const scale = Number($sheet.data('scale'));
    const moveUrl = $sheet.data('move-url');
    const csrf = $('input[name="_csrf"]').first().val();
    const $state = $('#save-state');
    const PX_PER_PT = 25.4 / 72 * scale;   // 1 pt = 0.3528 mm

    const mm = px => Math.round(px / scale * 10) / 10;
    const row = code => $('tr[data-code="' + code + '"]');
    const input = (code, k) => row(code).find('input[data-k="' + k + '"]');

    // Draw the sample text of a field the way the PDF does (date digits in boxes, crossing between lines)
    function render($f) {
        const code = $f.data('code');
        const text = String($f.data('text') || '');
        const size = Number($f.data('size'));
        const pitch = Number($f.data('pitch')) || 0;
        const px = size * PX_PER_PT;
        $f.css({'font-size': px + 'px', 'line-height': '1.2', 'min-height': (px * 1.2) + 'px'});
        $f.find('.designer-text').remove();
        const $t = $('<div class="designer-text"></div>').prependTo($f);
        if (code === 'DATE' && pitch > 0) {
            for (const ch of text) {
                $('<span class="designer-digit"></span>').css('width', (pitch * scale) + 'px').text(ch).appendTo($t);
            }
        } else if (code === 'CROSSING') {
            $t.addClass('designer-crossing').text(text || '(only on A/C payee cheques)');
            $f.css({'transform': 'rotate(' + (-Number($f.data('angle') || 0)) + 'deg)', 'transform-origin': 'left bottom'});
        } else if (code === 'AMOUNT_WORDS' || code === 'SIGNATORY') {
            const gap = Number($f.data('gap')) || 0;
            $t.css({'white-space': code === 'SIGNATORY' ? 'pre-line' : 'normal',
                    'line-height': gap > 0 ? (gap * scale) + 'px' : '1.6'}).text(text);
        } else {
            $t.text(text);
        }
    }

    function save($f) {
        const code = $f.data('code');
        const pos = $f.position();
        const data = {x: mm(pos.left), y: mm(pos.top), width: mm($f.outerWidth()), _csrf: csrf};
        $state.removeClass('text-danger text-success').addClass('text-secondary').text('Saving…');
        $.post(moveUrl + code + '/move', data)
            .done(r => {
                input(code, 'x').val(r.x);
                input(code, 'y').val(r.y);
                input(code, 'width').val(r.width);
                $state.removeClass('text-secondary').addClass('text-success').text('Saved ' + code.toLowerCase().replace('_', ' '));
            })
            .fail(x => {
                const msg = x.responseJSON && x.responseJSON.error ? x.responseJSON.error : 'Not saved. Reload the page.';
                $state.removeClass('text-secondary').addClass('text-danger').text(msg);
            });
    }

    const editable = !$sheet.hasClass('print-preview');   // the print page shows the same drawing, read-only
    $sheet.find('.designer-field').each(function () {
        const $f = $(this);
        render($f);
        if (!editable) return;
        $f.draggable({containment: '#sheet', stop: () => save($f)});
        if ($f.data('code') !== 'CROSSING') {   // a rotated box resizes awkwardly: type its width instead
            $f.resizable({handles: 'e', containment: '#sheet', minWidth: 10, stop: () => save($f)});
        }
    });

    // Typing exact values moves the field on screen; the table is saved with its own button
    $('.designer-table').on('input', 'input[data-k]', function () {
        const code = $(this).closest('tr').data('code');
        const $f = $sheet.find('.designer-field[data-code="' + code + '"]');
        const v = Number(this.value);
        if (Number.isNaN(v)) return;
        const k = $(this).data('k');
        $f.css(k === 'x' ? 'left' : (k === 'y' ? 'top' : 'width'), (v * scale) + 'px');
    });
});
