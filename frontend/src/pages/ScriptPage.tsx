import { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import type { Script, Project, ValidationResult } from '../types';
import * as projectService from '../services/projectService';
import * as scriptService from '../services/scriptService';
import { exportScript } from '../services/exportService';
import ScriptViewer from '../components/ScriptViewer';
import YamlEditor from '../components/YamlEditor';
import ValidationPanel from '../components/ValidationPanel';
import ExportDropdown from '../components/ExportDropdown';

export default function ScriptPage() {
  const { id, scriptId } = useParams<{ id: string; scriptId: string }>();
  const projectId = Number(id);
  const scriptIdNum = Number(scriptId);

  const [project, setProject] = useState<Project | null>(null);
  const [script, setScript] = useState<Script | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editMode, setEditMode] = useState(false);
  const [validationResult, setValidationResult] = useState<ValidationResult | null>(null);
  const [isValidating, setIsValidating] = useState(false);
  const [isFixing, setIsFixing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isExporting, setIsExporting] = useState(false);
  const [showValidation, setShowValidation] = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [projectData, scriptData] = await Promise.all([
        projectService.getProject(projectId),
        scriptService.getScript(scriptIdNum),
      ]);
      setProject(projectData);
      setScript(scriptData);
      if (scriptData.validationResult) {
        setValidationResult(scriptData.validationResult);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '获取数据失败');
    } finally {
      setLoading(false);
    }
  }, [projectId, scriptIdNum]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleValidate = async () => {
    if (!script) return;
    setIsValidating(true);
    setError(null);
    try {
      const result = await scriptService.validateScript(script.id);
      setValidationResult(result);
      setShowValidation(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : '校验失败');
    } finally {
      setIsValidating(false);
    }
  };

  const handleFix = async () => {
    if (!script) return;
    setIsFixing(true);
    setError(null);
    try {
      const fixed = await scriptService.fixScript(script.id);
      setScript(fixed);
      // Re-validate after fix
      const result = await scriptService.validateScript(fixed.id);
      setValidationResult(result);
      setShowValidation(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : '自动修复失败');
    } finally {
      setIsFixing(false);
    }
  };

  const handleSave = async (contentYaml: string) => {
    if (!script) return;
    setIsSaving(true);
    setError(null);
    try {
      const updated = await scriptService.updateScript(script.id, { contentYaml });
      setScript(updated);
      setEditMode(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
    } finally {
      setIsSaving(false);
    }
  };

  const handleExport = async (format: 'yaml' | 'markdown' | 'pdf') => {
    if (!script) return;
    setIsExporting(true);
    setError(null);
    try {
      await exportScript({ scriptId: script.id, format }, script.title || `script-v${script.version}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '导出失败');
    } finally {
      setIsExporting(false);
    }
  };

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <svg className="w-8 h-8 animate-spin text-primary-500" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
        <span className="ml-3 text-gray-500">加载中...</span>
      </div>
    );
  }

  if (!script) {
    return (
      <div className="text-center py-20">
        <svg className="mx-auto w-12 h-12 text-red-300 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
        <p className="text-red-600">{error || '剧本不存在'}</p>
        <Link to={`/project/${projectId}`} className="btn-primary mt-4 inline-block">返回项目</Link>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-[calc(100vh-8rem)]">
      {/* Breadcrumb */}
      <nav className="flex items-center space-x-2 text-sm text-gray-500 mb-4 flex-shrink-0">
        <Link to="/" className="hover:text-primary-600 transition-colors">首页</Link>
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
        <Link to={`/project/${projectId}`} className="hover:text-primary-600 transition-colors">
          {project?.title || '项目'}
        </Link>
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
        <span className="text-gray-900 font-medium">
          {script.title || `剧本 v${script.version}`}
        </span>
      </nav>

      {/* Error banner */}
      {error && (
        <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg flex items-center justify-between flex-shrink-0">
          <div className="flex items-center">
            <svg className="w-5 h-5 text-red-400 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span className="text-sm text-red-700">{error}</span>
          </div>
          <button onClick={() => setError(null)} className="text-red-400 hover:text-red-600">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      )}

      {/* Header & Toolbar */}
      <div className="card mb-4 flex-shrink-0 overflow-visible">
        <div className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-bold text-gray-900">
                {script.title || `剧本 v${script.version}`}
              </h1>
              <div className="flex items-center space-x-4 mt-1 text-xs text-gray-500">
                <span>版本 {script.version}</span>
                <span>创建于 {formatDate(script.createdAt)}</span>
                <span>更新于 {formatDate(script.updatedAt)}</span>
              </div>
            </div>
            <div className="flex items-center space-x-2">
              <button
                onClick={handleValidate}
                disabled={isValidating || editMode}
                className="btn-secondary text-sm"
              >
                {isValidating ? (
                  <span className="flex items-center space-x-1">
                    <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    <span>校验中...</span>
                  </span>
                ) : (
                  <>
                    <svg className="w-4 h-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    校验
                  </>
                )}
              </button>
              <button
                onClick={handleFix}
                disabled={isFixing || editMode}
                className="btn-secondary text-sm"
              >
                {isFixing ? (
                  <span className="flex items-center space-x-1">
                    <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                    </svg>
                    <span>修复中...</span>
                  </span>
                ) : (
                  <>
                    <svg className="w-4 h-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M11.049 2.927c.3-.921 1.603-.921 1.902 0l1.519 4.674a1 1 0 00.95.69h4.915c.969 0 1.371 1.24.588 1.81l-3.976 2.888a1 1 0 00-.363 1.118l1.518 4.674c.3.922-.755 1.688-1.538 1.118l-3.976-2.888a1 1 0 00-1.176 0l-3.976 2.888c-.783.57-1.838-.197-1.538-1.118l1.518-4.674a1 1 0 00-.363-1.118l-3.976-2.888c-.784-.57-.38-1.81.588-1.81h4.914a1 1 0 00.951-.69l1.519-4.674z" />
                    </svg>
                    自动修复
                  </>
                )}
              </button>
              <button
                onClick={() => setEditMode(!editMode)}
                className={`text-sm ${editMode ? 'btn-primary' : 'btn-secondary'}`}
              >
                <svg className="w-4 h-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
                {editMode ? '预览' : '编辑'}
              </button>
              <ExportDropdown onExport={handleExport} exporting={isExporting} />
            </div>
          </div>
        </div>
      </div>

      {/* Validation panel */}
      {showValidation && validationResult && (
        <div className="mb-4 flex-shrink-0">
          <ValidationPanel
            result={validationResult}
            onClose={() => setShowValidation(false)}
          />
        </div>
      )}

      {/* Main content */}
      <div className="flex-1 min-h-0 card overflow-hidden">
        {editMode ? (
          <YamlEditor
            content={script.contentYaml}
            onSave={handleSave}
            onCancel={() => setEditMode(false)}
            saving={isSaving}
          />
        ) : (
          <div className="h-full overflow-y-auto">
            <ScriptViewer contentYaml={script.contentYaml} />
          </div>
        )}
      </div>
    </div>
  );
}
