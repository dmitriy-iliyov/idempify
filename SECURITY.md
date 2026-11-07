# Security Policy

## Supported versions

`idempify` has not had a release yet. Security fixes are made on the `main` branch only.

| Version | Supported |
|---|---|
| `main` (`0.0.1-SNAPSHOT`) | yes |

## Reporting a vulnerability

Please **do not** report security vulnerabilities through public issues, discussions or pull requests.

Report them privately through GitHub:
[**Report a vulnerability**](https://github.com/dmitriy-iliyov/idempify/security/advisories/new).

Include as much of the following as you can:

- the affected module(s) and commit;
- a description of the issue and its impact - for an idempotency library, typically a way to get a business
  operation executed more than once under one key, to be answered with another request's response, or to bypass
  fingerprint validation;
- steps or a minimal application to reproduce it.

You can expect an acknowledgement within 7 days. Once the issue is confirmed, a fix is prepared and the
advisory is published together with it; reporters are credited unless they ask not to be.
