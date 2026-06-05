import type { ValidationResult } from '../types';

interface ValidationPanelProps {
  result: ValidationResult;
  onClose?: () => void;
}

export default function ValidationPanel({ result, onClose }: ValidationPanelProps) {
  return (
    <div className="border border-gray-200 rounded-lg overflow-hidden">
      <div className={`px-4 py-3 flex items-center justify-between ${
        result.valid ? 'bg-green-50' : 'bg-red-50'
      }`}>
        <div className="flex items-center space-x-2">
          {result.valid ? (
            <svg className="w-5 h-5 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          ) : (
            <svg className="w-5 h-5 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          )}
          <span className={`text-sm font-medium ${result.valid ? 'text-green-700' : 'text-red-700'}`}>
            {result.valid ? '校验通过' : '校验失败'}
          </span>
          {!result.valid && result.errors.length > 0 && (
            <span className="text-xs text-red-500">
              {result.errors.length} 个错误
            </span>
          )}
          {result.warnings.length > 0 && (
            <span className="text-xs text-amber-500">
              {result.warnings.length} 个警告
            </span>
          )}
        </div>
        {onClose && (
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition-colors">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        )}
      </div>

      {!result.valid && result.errors.length > 0 && (
        <div className="border-t border-gray-200">
          <div className="px-4 py-2 bg-red-50/50">
            <h4 className="text-xs font-medium text-red-600 uppercase tracking-wide mb-2">错误</h4>
            <ul className="space-y-1.5">
              {result.errors.map((err, i) => (
                <li key={i} className="flex items-start text-sm">
                  <svg className="w-4 h-4 text-red-400 mr-1.5 mt-0.5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01" />
                  </svg>
                  <div>
                    {err.path && (
                      <code className="text-xs bg-red-100 text-red-700 px-1 py-0.5 rounded mr-1">
                        {err.path}
                      </code>
                    )}
                    <span className="text-red-700">{err.message}</span>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {result.warnings.length > 0 && (
        <div className="border-t border-gray-200">
          <div className="px-4 py-2 bg-amber-50/50">
            <h4 className="text-xs font-medium text-amber-600 uppercase tracking-wide mb-2">警告</h4>
            <ul className="space-y-1.5">
              {result.warnings.map((warn, i) => (
                <li key={i} className="flex items-start text-sm">
                  <svg className="w-4 h-4 text-amber-400 mr-1.5 mt-0.5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01" />
                  </svg>
                  <div>
                    {warn.path && (
                      <code className="text-xs bg-amber-100 text-amber-700 px-1 py-0.5 rounded mr-1">
                        {warn.path}
                      </code>
                    )}
                    <span className="text-amber-700">{warn.message}</span>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </div>
  );
}
