package io.github.dmitriyiliyov.idempify.http;

import io.github.dmitriyiliyov.idempify.core.response.OperationState;
import io.github.dmitriyiliyov.idempify.core.response.OperationStateChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public class HttpAttributesOperationStateChannel implements OperationStateChannel {

    private static final Logger log = LoggerFactory.getLogger(HttpAttributesOperationStateChannel.class);
    private static final String ATTRIBUTE_NAME =
            HttpAttributesOperationStateChannel.class.getName() + ".IDEMPOTENT_OPERATION_STATE";

    @Override
    public void publish(OperationState state) {
        getRequestAttributes().setAttribute(ATTRIBUTE_NAME, state, RequestAttributes.SCOPE_REQUEST);
    }

    @Override
    public OperationState consume() {
        RequestAttributes attributes = getRequestAttributes();
        OperationState state = (OperationState) attributes.getAttribute(ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
        attributes.removeAttribute(ATTRIBUTE_NAME, RequestAttributes.SCOPE_REQUEST);
        return state;
    }

    private RequestAttributes getRequestAttributes() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("""
                    No HTTP request is bound to the current thread, so there is nowhere to keep the operation state; 
                    this channel only works on the thread that serves the request
            """);
            throw new IllegalStateException("No HTTP request is bound to the current thread");
        }
        return attributes;
    }
}
