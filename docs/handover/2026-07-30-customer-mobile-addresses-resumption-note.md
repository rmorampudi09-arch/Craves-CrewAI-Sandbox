# Customer Mobile Addresses — Resumption Note

The original implementation session paused after the address source, CI, navigation and handover had already been committed.

Validation performed on resumption:

- branch is exactly ahead of `feature/customer-mobile-notifications` with no missing parent commits;
- authenticated CRUD routes use the secure mobile session bearer;
- address and coordinate validation is bounded;
- customer identity IDs are not part of the public mobile DTO;
- CI blocks AsyncStorage, token logging and unreviewed native geolocation dependencies;
- device-location capture remains a native-shell amendment after Android/iOS projects and permission manifests exist;
- no pipeline, Azure action, Firebase action or native build was run.
