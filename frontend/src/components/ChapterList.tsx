import { useState } from 'react';
import type { Chapter } from '../types';

interface ChapterListProps {
  chapters: Chapter[];
  onDelete: (chapterNumber: number) => void;
  deleting?: number | null;
}

export default function ChapterList({ chapters, onDelete, deleting }: ChapterListProps) {
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const toggleExpand = (id: number) => {
    setExpandedId(expandedId === id ? null : id);
  };

  if (chapters.length === 0) {
    return (
      <div className="text-center py-12 text-gray-500">
        <svg className="mx-auto w-12 h-12 text-gray-300 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
        <p>暂无章节，请添加章节内容</p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {chapters.map((chapter) => (
        <div
          key={chapter.id}
          className="card hover:border-gray-300 transition-colors"
        >
          <div
            className="flex items-center justify-between p-4 cursor-pointer"
            onClick={() => toggleExpand(chapter.id)}
          >
            <div className="flex items-center space-x-3 flex-1 min-w-0">
              <span className="flex-shrink-0 w-8 h-8 bg-primary-50 text-primary-600 rounded-lg flex items-center justify-center text-sm font-medium">
                {chapter.chapterNumber}
              </span>
              <div className="flex-1 min-w-0">
                <h4 className="text-sm font-medium text-gray-900 truncate">
                  {chapter.title}
                </h4>
                <p className="text-xs text-gray-500 mt-0.5">
                  {chapter.wordCount.toLocaleString()} 字
                </p>
              </div>
              <svg
                className={`w-5 h-5 text-gray-400 transition-transform duration-200 ${expandedId === chapter.id ? 'rotate-180' : ''}`}
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={2}
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
              </svg>
            </div>
            <button
              onClick={(e) => {
                e.stopPropagation();
                onDelete(chapter.chapterNumber);
              }}
              disabled={deleting === chapter.chapterNumber}
              className="ml-3 p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-colors
                       disabled:opacity-50"
              title="删除章节"
            >
              {deleting === chapter.chapterNumber ? (
                <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
              ) : (
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              )}
            </button>
          </div>

          {expandedId === chapter.id && (
            <div className="border-t border-gray-100 p-4">
              <div className="text-sm text-gray-700 whitespace-pre-wrap leading-relaxed max-h-96 overflow-y-auto">
                {chapter.content || '（无内容）'}
              </div>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
