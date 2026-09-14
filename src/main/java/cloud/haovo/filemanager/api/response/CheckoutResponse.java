package cloud.haovo.filemanager.api.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CheckoutResponse {
    private String orderId;
    private long orderCode;
    private String checkoutUrl;
}
