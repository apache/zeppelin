import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class SaveAsService {
  saveAs(content: string, filename: string, extension: string) {
    const BOM = '\uFEFF';
    const fileName = `${filename}.${extension}`;
    const binaryData = [];
    binaryData.push(BOM);
    binaryData.push(content);
    const blob = new Blob(binaryData, { type: 'octet/stream' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    document.body.appendChild(a);
    a.style.display = 'none';
    a.href = url;
    a.download = fileName;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  constructor() {}
}
