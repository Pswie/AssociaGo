# Certificato pubblico Supabase Root 2021

`supabase-root-2021.crt` è il certificato CA pubblico ufficiale, scaricato via
HTTPS senza redirect il 30 ottobre 2026. Serve a verificare il certificato TLS
PostgreSQL; non contiene credenziali e non viene installato nello store globale.
Le connessioni applicative conservano la verifica completa del certificato e
del nome host (`verify-full`).

URL esatto del download:

<https://supabase-downloads.s3-ap-southeast-1.amazonaws.com/prod/ssl/prod-ca-2021.crt>

La provenienza è verificata su due sorgenti ufficiali indipendenti:

- [Supabase Studio, custom-content.json al commit 552312507f38f1852db23b292e023c36cddc40f3](https://github.com/supabase/supabase/blob/552312507f38f1852db23b292e023c36cddc40f3/apps/studio/hooks/custom-content/custom-content.json):
  `ssl:certificate_url` definisce il modello S3 `${env}/ssl/${env}-ca-2021.crt`.
  [SSLConfiguration.tsx dello stesso commit](https://github.com/supabase/supabase/blob/552312507f38f1852db23b292e023c36cddc40f3/apps/studio/components/interfaces/Settings/Database/SSLConfiguration.tsx)
  seleziona `prod` per l'ambiente di produzione.
- [Supabase CLI, prod-ca-2021.ts al commit 53169d20e2193facfcd03daa474fbebe3d7ca13b](https://github.com/supabase/cli/blob/53169d20e2193facfcd03daa474fbebe3d7ca13b/apps/cli/src/commands/gen/types/templates/prod-ca-2021.ts):
  il certificato incorporato ha lo stesso DER del file scaricato.

## Identità e verifica

| Proprietà | Valore verificato |
|---|---|
| Dimensione PEM | 1367 byte |
| SHA256 del file PEM | `700723581420dd1ac98fd7e9ac529f0ef210eadcaf87fc868a3ad7d114c2f3b7` |
| SHA256 del certificato DER | `807025ad50d4ed219d2c9c7d299c004f824eb00cf7f65afef607d07b72e6cafa` |
| Subject = Issuer | `CN=Supabase Root 2021 CA,O=Supabase Inc,L=New Castle,ST=Delware,C=US` |
| NotBefore UTC | `2021-04-28T10:56:53Z` |
| NotAfter UTC | `2031-04-26T10:56:53Z` |
| BasicConstraints | `CA:TRUE` |
| KeyUsage | firma di certificati e CRL consentita |

La grafia `Delware` appartiene al certificato originale. Sono state verificate
la firma RSA del certificato con la sua chiave pubblica, l'uguaglianza
Subject/Issuer e la validità temporale. Il file conserva esattamente i byte del
download; `.gitattributes` mantiene le terminazioni LF per stabilizzare l'hash.

Controllo locale del file da AssociaGo:

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath deployment/certs/supabase-root-2021.crt
```

Una futura rotazione richiede una nuova fonte ufficiale e nuove verifiche:
non sostituire il certificato per aggirare un errore TLS e non disattivare
la verifica del nome host o della catena.
