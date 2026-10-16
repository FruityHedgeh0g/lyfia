# Media files go through a swappable storage, PostgreSQL first

This specifies ADR 0008. The files of the Medias are stored and read through one storage interface (put, open as a stream, delete, exists) keyed by a file key; PostgreSQL (`media_contents`, keyed by file key) is its only implementation for now, and an object storage (S3-compatible, Azure Blob…) can replace it later by configuration. The `medias` table describes a Media and gives the keys of its files, with no database relation to their content. Each photo keeps its original and comes with a fixed list of variants (a display size of about 2000 px, a thumbnail of about 400 px). The browser of whoever deposits makes them before sending, and first scales the original down to the largest size the Super admin allows (3000 px and 5 MB by default): the server never decodes an image, it only checks types and weights, so depositing costs it neither memory nor computing. Only the Bureau and the Chefs de groupe deposit, so what their browser sends is trusted. The browser only ever asks `/api/medias/{id}/content` for a variant: served by the application now, it may redirect to a signed URL of the object storage later.

## Considered Options

- **Object storage right away**: rejected for now; an external service, credentials and costs, before the Galerie has proven its use.
- **Keeping only resized photos, not the original**: rejected; the original is wanted once the files move to an object storage, where it costs little.
- **The files served by the database relation, as before**: rejected; moving them would mean changing the model and the API.
- **Variants made by the server**: rejected; decoding a phone's photo takes 50 to 100 MB of memory, too much for a small server receiving a batch of 50, and image processing in the native executable needs AWT and system libraries the runtime image lacks.

## Consequences

- Until the move, the database grows with each original: within the 8 MB per file and a few hundred photos it holds, but it is to be watched.
- Moving to an object storage is a copy of every key, checked, then a change of configuration; the two storages can be read side by side meanwhile (the new one first).
- Making the variants again (another size, another format) takes a one-off tool, not the server.
- The Super admin may raise the largest size allowed once the files are in an object storage.
- A file never changes (a new version is a new key), so the one-year cache of its URL stays valid whatever the storage.
- Files are read and written as streams, so that no call has to change on the day of the move.
