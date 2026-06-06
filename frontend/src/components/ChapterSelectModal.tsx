import { useState, useMemo } from 'react';
import type { Chapter } from '../types';

interface ChapterSelectModalProps {
  chapters: Chapter[];
  analyzedChapters: number[];
  onConfirm: (selectedNumbers: number[]) => void;
  onCancel: () => void;
  loading?: boolean;
}

export default function ChapterSelectModal({
  chapters,
  analyzedChapters,
  onConfirm,
  onCancel,
  loading = false,
}: ChapterSelectModalProps) {
  const analyzedSet = useMemo(() => new Set(analyzedChapters), [analyzedChapters]);

  const newChapters = useMemo(
    () => chapters.filter((ch) => !analyzedSet.has(ch.chapterNumber)),
    [chapters, analyzedSet]
  );

  const prevChapters = useMemo(
    () => chapters.filter((ch) => analyzedSet.has(ch.chapterNumber)),
    [chapters, analyzedSet]
  );

  // Default: select all new chapters
  const [selected, setSelected] = useState<Set<number>>(
    () => new Set(newChapters.map((ch) => ch.chapterNumber))
  );

  const toggle = (num: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(num)) {
        next.delete(num);
      } else {
        next.add(num);
      }
      return next;
    });
  };

  const selectAllNew = () => {
    setSelected((prev) => {
      const next = new Set(prev);
      newChapters.forEach((ch) => next.add(ch.chapterNumber));
      return next;
    });
  };

  const deselectAllNew = () => {
    setSelected((prev) => {
      const next = new Set(prev);
      newChapters.forEach((ch) => next.delete(ch.chapterNumber));
      return next;
    });
  };

  const selectAllPrev = () => {
    setSelected((prev) => {
      const next = new Set(prev);
      prevChapters.forEach((ch) => next.add(ch.chapterNumber));
      return next;
    });
  };

  const deselectAllPrev = () => {
    setSelected((prev) => {
      const next = new Set(prev);
      prevChapters.forEach((ch) => next.delete(ch.chapterNumber));
      return next;
    });
  };

  const selectAll = () => {
    setSelected(new Set(chapters.map((ch) => ch.chapterNumber)));
  };

  const deselectAll = () => {
    setSelected(new Set());
  };

  const handleConfirm = () => {
    if (selected.size === 0) return;
    const sorted = Array.from(selected).sort((a, b) => a - b);
    onConfirm(sorted);
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg max-h-[85vh] flex flex-col">
        {/* Header */}
        <div className="p-5 border-b border-gray-200">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold text-gray-900">选择要分析的章节</h2>
            <button
              onClick={onCancel}
              className="p-1.5 text-gray-400 hover:text-gray-600 rounded-lg hover:bg-gray-100 transition-colors"
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <p className="text-sm text-gray-500 mt-1">
            选中的章节将进行 AI 分析并生成剧本，未选中的章节将保留已有的分析结果。
          </p>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-5 space-y-5">
          {/* Quick actions */}
          <div className="flex items-center gap-2 flex-wrap">
            <button onClick={selectAll} className="text-xs px-2.5 py-1 rounded-full bg-gray-100 text-gray-600 hover:bg-gray-200 transition-colors">
              全选
            </button>
            <button onClick={deselectAll} className="text-xs px-2.5 py-1 rounded-full bg-gray-100 text-gray-600 hover:bg-gray-200 transition-colors">
              取消全选
            </button>
          </div>

          {/* New chapters section */}
          {newChapters.length > 0 && (
            <div>
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-sm font-semibold text-primary-700 flex items-center">
                  <span className="w-2 h-2 bg-primary-500 rounded-full mr-2"></span>
                  新增章节（未分析）
                </h3>
                <div className="flex gap-1.5">
                  <button onClick={selectAllNew} className="text-xs text-primary-600 hover:text-primary-800">
                    全选
                  </button>
                  <span className="text-gray-300">|</span>
                  <button onClick={deselectAllNew} className="text-xs text-gray-500 hover:text-gray-700">
                    取消
                  </button>
                </div>
              </div>
              <div className="space-y-1.5">
                {newChapters.map((ch) => (
                  <label
                    key={ch.chapterNumber}
                    className={`flex items-center p-2.5 rounded-lg cursor-pointer transition-colors ${
                      selected.has(ch.chapterNumber)
                        ? 'bg-primary-50 border border-primary-200'
                        : 'bg-gray-50 border border-transparent hover:bg-gray-100'
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={selected.has(ch.chapterNumber)}
                      onChange={() => toggle(ch.chapterNumber)}
                      className="w-4 h-4 text-primary-600 rounded border-gray-300 focus:ring-primary-500 mr-3"
                    />
                    <span className="flex-shrink-0 w-7 h-7 bg-primary-100 text-primary-700 rounded-md flex items-center justify-center text-xs font-semibold mr-2.5">
                      {ch.chapterNumber}
                    </span>
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-gray-900 truncate">{ch.title}</div>
                      <div className="text-xs text-gray-500">{ch.wordCount.toLocaleString()} 字</div>
                    </div>
                    <span className="flex-shrink-0 text-xs px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 ml-2">
                      新
                    </span>
                  </label>
                ))}
              </div>
            </div>
          )}

          {/* Previously analyzed chapters section */}
          {prevChapters.length > 0 && (
            <div>
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-sm font-semibold text-gray-600 flex items-center">
                  <span className="w-2 h-2 bg-gray-400 rounded-full mr-2"></span>
                  已分析章节
                </h3>
                <div className="flex gap-1.5">
                  <button onClick={selectAllPrev} className="text-xs text-primary-600 hover:text-primary-800">
                    全选
                  </button>
                  <span className="text-gray-300">|</span>
                  <button onClick={deselectAllPrev} className="text-xs text-gray-500 hover:text-gray-700">
                    取消
                  </button>
                </div>
              </div>
              <div className="space-y-1.5">
                {prevChapters.map((ch) => (
                  <label
                    key={ch.chapterNumber}
                    className={`flex items-center p-2.5 rounded-lg cursor-pointer transition-colors ${
                      selected.has(ch.chapterNumber)
                        ? 'bg-primary-50 border border-primary-200'
                        : 'bg-gray-50 border border-transparent hover:bg-gray-100'
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={selected.has(ch.chapterNumber)}
                      onChange={() => toggle(ch.chapterNumber)}
                      className="w-4 h-4 text-primary-600 rounded border-gray-300 focus:ring-primary-500 mr-3"
                    />
                    <span className="flex-shrink-0 w-7 h-7 bg-gray-200 text-gray-600 rounded-md flex items-center justify-center text-xs font-semibold mr-2.5">
                      {ch.chapterNumber}
                    </span>
                    <div className="flex-1 min-w-0">
                      <div className="text-sm font-medium text-gray-900 truncate">{ch.title}</div>
                      <div className="text-xs text-gray-500">{ch.wordCount.toLocaleString()} 字</div>
                    </div>
                    <span className="flex-shrink-0 text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-700 ml-2">
                      已分析
                    </span>
                  </label>
                ))}
              </div>
            </div>
          )}

          {chapters.length === 0 && (
            <div className="text-center py-8 text-gray-500">
              <p>暂无章节，请先添加章节内容</p>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="p-5 border-t border-gray-200">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm text-gray-500">
              已选择 <span className="font-semibold text-primary-600">{selected.size}</span> / {chapters.length} 章
            </span>
          </div>
          <div className="flex gap-2">
            <button
              onClick={onCancel}
              className="flex-1 btn-secondary text-sm"
              disabled={loading}
            >
              取消
            </button>
            <button
              onClick={handleConfirm}
              disabled={selected.size === 0 || loading}
              className="flex-1 btn-primary text-sm disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? (
                <span className="flex items-center justify-center space-x-1.5">
                  <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  <span>分析中...</span>
                </span>
              ) : (
                '开始分析'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
