package com.chequetrack.attachment.web;

import com.chequetrack.attachment.domain.Attachment;
import com.chequetrack.attachment.domain.AttachmentCategory;
import com.chequetrack.attachment.service.AttachmentService;
import com.chequetrack.attachment.service.AttachmentService.StoredFile;
import com.chequetrack.common.exception.BusinessException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

/**
 * Attachments page of a record, upload, download and remove.
 * Every request checks access through the owner record (see AttachmentOwnerResolver).
 */
@Controller
@RequestMapping("/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping("/{ownerType}/{ownerId}")
    public String page(@PathVariable String ownerType, @PathVariable Long ownerId, Model model) {
        model.addAttribute("owner", attachmentService.owner(ownerType, ownerId));
        model.addAttribute("ownerType", ownerType);
        model.addAttribute("ownerId", ownerId);
        model.addAttribute("attachments", attachmentService.list(ownerType, ownerId));
        model.addAttribute("categories", AttachmentCategory.values());
        return "attachment/page";
    }

    @PostMapping("/{ownerType}/{ownerId}")
    public String upload(@PathVariable String ownerType, @PathVariable Long ownerId,
                         @RequestParam(required = false) AttachmentCategory category,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) MultipartFile file,
                         RedirectAttributes redirect) {
        try {
            Attachment saved = attachmentService.upload(ownerType, ownerId, category, description, file);
            redirect.addFlashAttribute("successMessage", "'" + saved.getOriginalFilename() + "' attached.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/attachments/" + ownerType + "/" + ownerId;
    }

    /** Download; {@code inline=true} opens PDFs and images in the browser instead. */
    @GetMapping("/file/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean inline) {
        StoredFile file = attachmentService.download(id);
        Attachment a = file.attachment();
        ContentDisposition disposition = (inline && a.isPreviewable() ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(a.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .contentLength(a.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new FileSystemResource(file.path()));
    }

    @PostMapping("/file/{id}/remove")
    public String remove(@PathVariable Long id, RedirectAttributes redirect) {
        Attachment removed;
        try {
            removed = attachmentService.remove(id);
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/";
        }
        redirect.addFlashAttribute("successMessage", "'" + removed.getOriginalFilename() + "' removed.");
        return "redirect:/attachments/" + removed.getOwnerType() + "/" + removed.getOwnerId();
    }
}
