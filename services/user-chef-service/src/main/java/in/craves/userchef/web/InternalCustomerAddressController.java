package in.craves.userchef.web;

import in.craves.userchef.security.InternalRequestAuthorizer;
import in.craves.userchef.service.CustomerProfileService;
import in.craves.userchef.web.ApiDtos.CustomerAddressResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/customer-addresses")
public class InternalCustomerAddressController {
    private static final String INTERNAL_HEADER = "X-Craves-Internal-Secret";

    private final CustomerProfileService service;
    private final InternalRequestAuthorizer authorizer;

    public InternalCustomerAddressController(
        CustomerProfileService service,
        InternalRequestAuthorizer authorizer
    ) {
        this.service = service;
        this.authorizer = authorizer;
    }

    @GetMapping("/{addressId}")
    public CustomerAddressResponse getAddress(
        @RequestHeader(name = INTERNAL_HEADER, required = false) String suppliedSecret,
        @RequestParam UUID identityId,
        @PathVariable UUID addressId
    ) {
        authorizer.requireValid(suppliedSecret);
        return service.getAddressForInternal(identityId, addressId);
    }
}
