// ChequeTrack shared scripts.
$(function () {
    // Highlight the sidebar link for the current page.
    const path = window.location.pathname;
    $('.app-sidebar .nav-link').each(function () {
        const href = $(this).attr('href');
        if (href === path || (href !== '/' && path.startsWith(href))) {
            $(this).addClass('active');
        }
    });

    // Success messages close themselves after a few seconds.
    setTimeout(function () {
        $('.alert-success.alert-dismissible').alert('close');
    }, 5000);
});
