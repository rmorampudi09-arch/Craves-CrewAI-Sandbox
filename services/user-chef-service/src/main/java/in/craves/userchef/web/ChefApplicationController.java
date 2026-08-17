package in.craves.userchef.web;

import in.craves.userchef.security.CurrentUser;
import in.craves.userchef.service.ChefApplicationService;
import in.craves.userchef.web.ApiDtos.ChefApplicationRequest;
import in.craves.userchef.web.ApiDtos.ChefApplicationResponse;
import in.craves.userchef.web.ApiDtos.KycDocumentResponse;
import in.craves.userchef.web.ApiDtos.KycDocumentType;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/chef/application")
public class ChefApplicationController {
    private final ChefApplicationService service;

    public ChefApplicationController(ChefApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ChefApplicationResponse getMyApplication(@AuthenticationPrincipal CurrentUser user) {
        return service.getMyApplication(user);
    }

    @GetMapping(params = "evidence=true")
    public List<KycDocumentResponse> getMyEvidence(@AuthenticationPrincipal CurrentUser user) {
        return service.listMyApplicationEvidence(user);
    }

    @PostMapping
    public ChefApplicationResponse submitApplication(
        @AuthenticationPrincipal CurrentUser user,
        @Valid @RequestBody ChefApplicationRequest request
    ) {
        return service.submitApplication(user, request);
    }

    @PostMapping(value = "/proof-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public KycDocumentResponse uploadProofFile(
        @AuthenticationPrincipal CurrentUser user,
        @RequestParam KycDocumentType documentType,
        @RequestParam MultipartFile file
    ) {
        return service.uploadDocument(user, documentType, file);
    }
}
