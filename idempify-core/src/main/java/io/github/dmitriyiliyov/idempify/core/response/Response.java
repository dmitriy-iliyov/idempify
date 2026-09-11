package io.github.dmitriyiliyov.idempify.core.response;

import java.util.Map;

/**
 * A completed operation's answer, kept on the operation's own record so a repeat of the same call can be
 * answered without running the business logic again.
 * <p>
 * What a transport needs to reproduce that answer and nothing beyond it. One entry writes it, every later
 * call only reads it.
 */
public interface Response {

    int getStatus();

    byte [] getBody();

    String getContentType();

    /**
     * The headers the answer is reproduced with, never {@code null}: a record that kept none reads back as an
     * empty map. A transport writes them out without a guard, so an implementation may not answer
     * {@code null} here.
     */
    Map<String, String> getHeaders();
}
