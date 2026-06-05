import { useState, useRef, useEffect } from 'react';

interface ExportDropdownProps {
  onExport: (format: 'yaml' | 'markdown' | 'pdf') => void;
  exporting?: boolean;
}

const FORMATS = [
  { value: 'yaml' as const, label: 'YAML', icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z', desc: '结构化数据格式' },
  { value: 'markdown' as const, label: 'Markdown', icon: 'M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z', desc: '通用文档格式' },
  { value: 'pdf' as const, label: 'PDF', icon: 'M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z', desc: '打印友好格式' },
];

export default function ExportDropdown({ onExport, exporting }: ExportDropdownProps) {
  const [open, setOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        onClick={() => setOpen(!open)}
        disabled={exporting}
        className="btn-secondary text-sm"
      >
        {exporting ? (
          <span className="flex items-center space-x-1.5">
            <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            <span>导出中...</span>
          </span>
        ) : (
          <span className="flex items-center space-x-1.5">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            <span>导出</span>
            <svg className={`w-3 h-3 transition-transform ${open ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
            </svg>
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-48 bg-white rounded-lg shadow-lg border border-gray-200 z-50 overflow-hidden">
          {FORMATS.map((format) => (
            <button
              key={format.value}
              onClick={() => {
                onExport(format.value);
                setOpen(false);
              }}
              disabled={exporting}
              className="w-full flex items-center space-x-3 px-4 py-2.5 text-left hover:bg-gray-50
                       disabled:opacity-50 transition-colors"
            >
              <svg className="w-5 h-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d={format.icon} />
              </svg>
              <div>
                <div className="text-sm font-medium text-gray-700">{format.label}</div>
                <div className="text-xs text-gray-400">{format.desc}</div>
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
