// "Scan the cheque" card (fragments/scan.html): sends the picture to the server for OCR of the
// MICR line and fills in the cheque number, drawer bank branch and account number.
$(function () {
    const $card = $('#scanCard');
    if (!$card.length) return;

    const scanUrl = $card.data('scan-url');
    const numberField = '#' + $card.data('number-field');
    const keep = document.getElementById('scanFile');
    const csrf = $('input[name="_csrf"]').first().val();
    const MAX_SIDE = 3000; // pixels; plenty for the MICR digits and keeps phone photos under the upload limit

    // ---- picking a picture ----------------------------------------------------------------------

    $card.find('.scan-source').on('change', function () {
        if (this.files.length) use(this.files[0]);
        this.value = '';
    });

    $('#scanClear').on('click', function () {
        setKept(null);
        $('#scanPreview').addClass('d-none').removeAttr('src');
        $('#scanRawBox').addClass('d-none');
        $('#scanStatus').empty();
        $(this).addClass('d-none');
    });

    async function use(file) {
        let picture = file;
        if (file.type.startsWith('image/')) {
            try {
                picture = await shrink(file);
            } catch (e) {
                // the browser could not decode it (e.g. HEIC on a desktop): send it as it is
            }
        }
        setKept(picture);
        if (picture.type.startsWith('image/')) {
            $('#scanPreview').attr('src', URL.createObjectURL(picture)).removeClass('d-none');
        } else {
            $('#scanPreview').addClass('d-none');
        }
        $('#scanClear').removeClass('d-none');
        read(picture);
    }

    /** Re-encodes a photo as JPEG, upright (EXIF rotation applied) and at most MAX_SIDE pixels. */
    async function shrink(file) {
        const bmp = await createImageBitmap(file, {imageOrientation: 'from-image'});
        const scale = Math.min(1, MAX_SIDE / Math.max(bmp.width, bmp.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.round(bmp.width * scale);
        canvas.height = Math.round(bmp.height * scale);
        canvas.getContext('2d').drawImage(bmp, 0, 0, canvas.width, canvas.height);
        bmp.close();
        const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.92));
        if (!blob) throw new Error('encode failed');
        return new File([blob], (file.name || 'cheque').replace(/\.[^.]*$/, '') + '.jpg', {type: 'image/jpeg'});
    }

    /** Puts the picture into the form's file input, so it is saved with the cheque. */
    function setKept(file) {
        if (!keep) return;
        const dt = new DataTransfer();
        if (file) dt.items.add(file);
        keep.files = dt.files;
    }

    // ---- reading it on the server ---------------------------------------------------------------

    function read(file) {
        const data = new FormData();
        data.append('file', file);
        if (csrf) data.append('_csrf', csrf);
        $('#scanRawBox').addClass('d-none');
        status('secondary', '<span class="spinner-border spinner-border-sm me-1"></span> Reading the cheque…');

        fetch(scanUrl, {method: 'POST', body: data, credentials: 'same-origin'})
            .then(r => r.json().catch(() => ({})).then(body => ({ok: r.ok, code: r.status, body})))
            .then(({ok, code, body}) => {
                if (!ok) {
                    status('danger', body.message || (code === 403 ? 'You are not allowed to do this.' : 'Couldn\'t read the scan. Please type the details.'));
                    return;
                }
                if (body.rawText) {
                    $('#scanRaw').text(body.rawText);
                    $('#scanRawBox').removeClass('d-none');
                }
                if (!body.found) {
                    status('warning', body.message);
                    return;
                }
                const filled = [];
                fill(numberField, body.chequeNumber, 'cheque number', filled);
                if (body.bankBranchId) fill('#drawerBankBranchId', String(body.bankBranchId), 'bank branch', filled);
                fill('#drawerAccountNumber', body.accountNumber, 'account number', filled);
                status(body.bankBranchId ? 'success' : 'warning',
                    escape(body.message) + (filled.length ? ' Filled: ' + filled.join(', ') + '.' : ''), true);
            })
            .catch(() => status('danger', 'Couldn\'t reach the server. Please type the details.'));
    }

    function fill(selector, value, label, filled) {
        const $f = $(selector);
        if (!$f.length || !value) return;
        if ($f.is('select') && !$f.find('option[value="' + value + '"]').length) return;
        $f.val(value).addClass('scan-filled').trigger('input').trigger('change');
        filled.push(label);
    }

    // a highlighted field goes back to normal once the user edits it
    $(document).on('input change', '.scan-filled', function (e) {
        if (e.originalEvent) $(this).removeClass('scan-filled');
    });

    function status(kind, html, isHtml) {
        const icon = {success: 'check-circle', warning: 'exclamation-triangle', danger: 'x-circle', secondary: ''}[kind];
        const body = kind === 'secondary' || isHtml ? html : escape(html);
        $('#scanStatus').attr('class', 'small text-' + (kind === 'warning' ? 'warning-emphasis' : kind))
            .html((icon ? '<i class="bi bi-' + icon + '"></i> ' : '') + body);
    }

    function escape(s) {
        return $('<div>').text(s || '').html();
    }

    // ---- laptop webcam --------------------------------------------------------------------------

    const canUseCamera = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
    if (!canUseCamera) return;

    const $modal = $('#scanWebcamModal');
    const video = document.getElementById('scanVideo');
    const guide = document.getElementById('scanGuide');
    const CHEQUE_RATIO = 2.3; // width / height of a Bangladeshi cheque leaf
    let stream = null;
    let frame = null; // guide rectangle as fractions of the video picture

    $('#scanWebcamBtn').removeClass('d-none').on('click', function () {
        bootstrap.Modal.getOrCreateInstance($modal[0]).show();
    });

    $modal.on('shown.bs.modal', async function () {
        $('#scanCamError').addClass('d-none');
        $('#scanCapture').prop('disabled', true);
        try {
            stream = await navigator.mediaDevices.getUserMedia({
                audio: false,
                video: {facingMode: {ideal: 'environment'}, width: {ideal: 1920}, height: {ideal: 1080}}
            });
            video.srcObject = stream;
        } catch (e) {
            const msg = e && e.name === 'NotAllowedError' ? 'Camera access was refused. Allow the camera for this site and try again.'
                : e && e.name === 'NotFoundError' ? 'No camera was found.'
                    : 'The camera could not be started' + (e && e.message ? ': ' + e.message : '.');
            $('#scanCamError').text(msg).removeClass('d-none');
        }
    });

    $modal.on('hidden.bs.modal', function () {
        if (stream) stream.getTracks().forEach(t => t.stop());
        stream = null;
        video.srcObject = null;
    });

    video.addEventListener('loadedmetadata', function () {
        placeGuide();
        $('#scanCapture').prop('disabled', false);
    });
    $(window).on('resize', placeGuide);

    /** The largest cheque-shaped box that fits in 90% of the picture, centred. */
    function placeGuide() {
        if (!video.videoWidth) return;
        const W = video.clientWidth, H = video.clientHeight;
        let w = W * 0.9, h = w / CHEQUE_RATIO;
        if (h > H * 0.9) {
            h = H * 0.9;
            w = h * CHEQUE_RATIO;
        }
        frame = {x: (W - w) / 2 / W, y: (H - h) / 2 / H, w: w / W, h: h / H};
        Object.assign(guide.style, {
            left: frame.x * 100 + '%', top: frame.y * 100 + '%', width: frame.w * 100 + '%', height: frame.h * 100 + '%'
        });
    }

    $('#scanCapture').on('click', function () {
        if (!frame || !video.videoWidth) return;
        const vw = video.videoWidth, vh = video.videoHeight;
        const canvas = document.createElement('canvas');
        canvas.width = Math.round(frame.w * vw);
        canvas.height = Math.round(frame.h * vh);
        canvas.getContext('2d').drawImage(video, frame.x * vw, frame.y * vh, canvas.width, canvas.height,
            0, 0, canvas.width, canvas.height);
        canvas.toBlob(function (blob) {
            if (!blob) return;
            bootstrap.Modal.getOrCreateInstance($modal[0]).hide();
            use(new File([blob], 'cheque-webcam.jpg', {type: 'image/jpeg'}));
        }, 'image/jpeg', 0.95);
    });
});
