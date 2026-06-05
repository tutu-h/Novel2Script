import { useState, useRef, useEffect, useCallback } from 'react';

interface YamlEditorProps {
  content: string;
  onSave: (content: string) => void;
  onCancel: () => void;
  saving?: boolean;
}

export default function YamlEditor({ content, onSave, onCancel, saving }: YamlEditorProps) {
  const [text, setText] = useState(content);
  const [modified, setModified] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const lineNumbersRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setText(content);
    setModified(false);
  }, [content]);

  const lineCount = text.split('\n').length;

  const syncScroll = useCallback(() => {
    if (textareaRef.current && lineNumbersRef.current) {
      lineNumbersRef.current.scrollTop = textareaRef.current.scrollTop;
    }
  }, []);

  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setText(e.target.value);
    setModified(true);
  };

  const handleSave = () => {
    onSave(text);
  };

  const handleCancel = () => {
    if (modified) {
      if (window.confirm('有未保存的更改，确定要放弃吗？')) {
        onCancel();
      }
    } else {
      onCancel();
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
      e.preventDefault();
      if (modified) handleSave();
    }
    if (e.key === 'Tab') {
      e.preventDefault();
      const textarea = textareaRef.current;
      if (textarea) {
        const start = textarea.selectionStart;
        const end = textarea.selectionEnd;
        const newText = text.substring(0, start) + '  ' + text.substring(end);
        setText(newText);
        setModified(true);
        setTimeout(() => {
          textarea.selectionStart = textarea.selectionEnd = start + 2;
        }, 0);
      }
    }
  };

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between px-4 py-2 bg-gray-50 border-b border-gray-200">
        <div className="flex items-center space-x-2">
          <span className="text-sm font-medium text-gray-700">YAML 编辑器</span>
          {modified && (
            <span className="text-xs text-amber-600 bg-amber-50 px-2 py-0.5 rounded">未保存</span>
          )}
        </div>
        <div className="flex items-center space-x-2">
          <button
            onClick={handleCancel}
            className="btn-secondary text-sm px-3 py-1.5"
            disabled={saving}
          >
            取消
          </button>
          <button
            onClick={handleSave}
            disabled={!modified || saving}
            className="btn-primary text-sm px-3 py-1.5"
          >
            {saving ? (
              <span className="flex items-center space-x-1">
                <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
                <span>保存中...</span>
              </span>
            ) : '保存'}
          </button>
        </div>
      </div>

      <div className="flex flex-1 min-h-0 overflow-hidden">
        <div
          ref={lineNumbersRef}
          className="flex-shrink-0 w-12 bg-gray-50 border-r border-gray-200 overflow-hidden select-none"
        >
          <div className="py-3 text-right pr-2">
            {Array.from({ length: lineCount }, (_, i) => (
              <div key={i} className="text-xs text-gray-400 leading-6 h-6">
                {i + 1}
              </div>
            ))}
          </div>
        </div>
        <textarea
          ref={textareaRef}
          value={text}
          onChange={handleChange}
          onScroll={syncScroll}
          onKeyDown={handleKeyDown}
          className="flex-1 p-3 font-mono text-sm leading-6 resize-none focus:outline-none bg-white text-gray-800
                   min-h-[400px]"
          spellCheck={false}
          placeholder="在此编辑 YAML 内容..."
        />
      </div>

      <div className="px-4 py-1.5 bg-gray-50 border-t border-gray-200 flex items-center justify-between text-xs text-gray-500">
        <span>{lineCount} 行</span>
        <span>Ctrl+S 保存 | Tab 缩进</span>
      </div>
    </div>
  );
}
