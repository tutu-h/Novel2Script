import api from './api';
import type { ExportRequest } from '../types';

const FORMAT_EXTENSIONS: Record<string, string> = {
  yaml: 'yaml',
  markdown: 'md',
  pdf: 'pdf',
};

const FORMAT_MIME_TYPES: Record<string, string> = {
  yaml: 'text/yaml',
  markdown: 'text/markdown',
  pdf: 'application/pdf',
};

export async function exportScript(data: ExportRequest, filename?: string): Promise<void> {
  const response = await api.post('/export', data, {
    responseType: 'blob',
  });

  const blob = new Blob([response.data], {
    type: FORMAT_MIME_TYPES[data.format] || 'application/octet-stream',
  });

  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;

  const ext = FORMAT_EXTENSIONS[data.format] || data.format;
  const baseName = filename || `script-${data.scriptId}`;
  link.download = `${baseName}.${ext}`;

  document.body.appendChild(link);
  link.click();

  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}
