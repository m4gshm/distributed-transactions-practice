package payments.api;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
public class GlobalErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        var errorAttributes = super.getErrorAttributes(request, options);
        var error = getError(request);
        if (error instanceof ResponseStatusException responseStatusException) {
            var headers = responseStatusException.getHeaders();
            errorAttributes.putAll(headers);
        }
        return errorAttributes;
    }
}
