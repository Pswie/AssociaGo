/** Keep user-authored HTML isolated from the application, its cookies and styles. */
export default function HtmlPreview({ html, title, minHeight = 240 }) {
  const documentHtml = `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'"><style>body{margin:0;padding:16px;font-family:Segoe UI,Arial,sans-serif;line-height:1.5;overflow-wrap:anywhere;background:white;color:#212529}img{max-width:100%;height:auto}table{max-width:100%}</style></head><body>${html || ''}</body></html>`;

  return (
    <iframe
      title={title}
      sandbox=""
      referrerPolicy="no-referrer"
      srcDoc={documentHtml}
      className="w-100 border-0 rounded"
      style={{ minHeight, display: 'block', background: '#fff' }}
    />
  );
}
