import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import type { Project, Chapter, Analysis, AddChapterRequest } from '../types';
import * as projectService from '../services/projectService';
import * as scriptService from '../services/scriptService';
import ChapterList from '../components/ChapterList';
import ChapterSelectModal from '../components/ChapterSelectModal';

type Tab = 'chapters' | 'analysis' | 'scripts';

export default function ProjectPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const projectId = Number(id);

  const [project, setProject] = useState<Project | null>(null);
  const [chapters, setChapters] = useState<Chapter[]>([]);
  const [analysis, setAnalysis] = useState<Analysis | null>(null);
  const [scripts, setScripts] = useState<Array<{ id: number; version: number; title: string; createdAt: string }>>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<Tab>('chapters');

  // Chapter form state
  const [showAddChapter, setShowAddChapter] = useState(false);
  const [showBatchImport, setShowBatchImport] = useState(false);
  const [chapterForm, setChapterForm] = useState<AddChapterRequest>({ title: '', content: '' });
  const [batchText, setBatchText] = useState('');
  const [saving, setSaving] = useState(false);
  const [deletingChapter, setDeletingChapter] = useState<number | null>(null);

  // Edit project state
  const [editing, setEditing] = useState(false);
  const [editForm, setEditForm] = useState({ title: '', author: '', description: '' });


  // Script generation state
  const [generating, setGenerating] = useState(false);
  const [showChapterSelect, setShowChapterSelect] = useState(false);
  const [deletingScript, setDeletingScript] = useState<number | null>(null);
  const [expandedChapters, setExpandedChapters] = useState<Set<number>>(new Set());

  const fetchProject = useCallback(async () => {
    try {
      const data = await projectService.getProject(projectId);
      setProject(data);
      setEditForm({ title: data.title, author: data.author, description: data.description });
    } catch (err) {
      setError(err instanceof Error ? err.message : '获取项目信息失败');
    }
  }, [projectId]);

  const fetchChapters = useCallback(async () => {
    try {
      const data = await projectService.getChapters(projectId);
      setChapters(data);
    } catch {
      // Non-critical, continue
    }
  }, [projectId]);

  const fetchScripts = useCallback(async () => {
    try {
      const data = await scriptService.getProjectScripts(projectId);
      setScripts(data);
    } catch {
      // Non-critical, continue
    }
  }, [projectId]);

  const fetchAnalysis = useCallback(async () => {
    try {
      const data = await scriptService.getAnalysis(projectId);
      setAnalysis(data);
    } catch {
      // Analysis may not exist yet
      setAnalysis(null);
    }
  }, [projectId]);

  useEffect(() => {
    const init = async () => {
      setLoading(true);
      setError(null);
      await fetchProject();
      await Promise.all([fetchChapters(), fetchScripts(), fetchAnalysis()]);
      setLoading(false);
    };
    init();
  }, [fetchProject, fetchChapters, fetchScripts, fetchAnalysis]);

  const handleAddChapter = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!chapterForm.title.trim()) return;
    setSaving(true);
    try {
      await projectService.addChapter(projectId, chapterForm);
      setChapterForm({ title: '', content: '' });
      setShowAddChapter(false);
      await fetchChapters();
      await fetchProject();
    } catch (err) {
      setError(err instanceof Error ? err.message : '添加章节失败');
    } finally {
      setSaving(false);
    }
  };

  const handleBatchImport = async () => {
    if (!batchText.trim()) return;
    setSaving(true);
    try {
      await projectService.batchAddChapters(projectId, { text: batchText });
      setBatchText('');
      setShowBatchImport(false);
      await fetchChapters();
      await fetchProject();
    } catch (err) {
      setError(err instanceof Error ? err.message : '批量导入失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteChapter = async (chapterNumber: number) => {
    if (!window.confirm(`确定要删除第 ${chapterNumber} 章吗？`)) return;
    setDeletingChapter(chapterNumber);
    try {
      await projectService.deleteChapter(projectId, chapterNumber);
      await fetchChapters();
      await fetchProject();
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除章节失败');
    } finally {
      setDeletingChapter(null);
    }
  };

  const handleUpdateProject = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      const updated = await projectService.updateProject(projectId, editForm);
      setProject(updated);
      setEditing(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新项目失败');
    } finally {
      setSaving(false);
    }
  };

  const handleGenerateScript = async () => {
    // Show chapter selection modal instead of generating directly
    setShowChapterSelect(true);
  };

  const handleIncrementalGenerate = async (selectedChapterNumbers: number[]) => {
    setGenerating(true);
    setError(null);
    setShowChapterSelect(false);
    try {
      const result = await scriptService.generateIncremental({
        projectId,
        chapterNumbers: selectedChapterNumbers,
      });
      await fetchScripts();
      await fetchAnalysis();
      navigate(`/project/${projectId}/script/${result.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : '生成剧本失败');
    } finally {
      setGenerating(false);
    }
  };

  const handleDeleteScript = async (scriptId: number, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!window.confirm('确定要删除这个剧本吗？')) return;
    setDeletingScript(scriptId);
    try {
      await scriptService.deleteScript(scriptId);
      await fetchScripts();
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除剧本失败');
    } finally {
      setDeletingScript(null);
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

  const extractChapterBadge = (title?: string) => {
    if (!title) return '';
    const match = title.match(/章节(.+)/);
    return match ? match[1] : '';
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

  if (error && !project) {
    return (
      <div className="text-center py-20">
        <svg className="mx-auto w-12 h-12 text-red-300 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
        <p className="text-red-600">{error}</p>
        <Link to="/" className="btn-primary mt-4 inline-block">返回首页</Link>
      </div>
    );
  }

  if (!project) return null;

  const tabs: { key: Tab; label: string }[] = [
    { key: 'chapters', label: '章节管理' },
    { key: 'analysis', label: '小说整体分析' },
    { key: 'scripts', label: '剧本版本' },
  ];

  return (
    <div>
      {/* Breadcrumb */}
      <nav className="flex items-center space-x-2 text-sm text-gray-500 mb-6">
        <Link to="/" className="hover:text-primary-600 transition-colors">首页</Link>
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
        <span className="text-gray-900 font-medium">{project.title}</span>
      </nav>

      {/* Error banner */}
      {error && (
        <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg flex items-center justify-between">
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

      {/* Project header */}
      <div className="card mb-6">
        <div className="p-6">
          {editing ? (
            <form onSubmit={handleUpdateProject}>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">项目名称</label>
                  <input
                    type="text"
                    value={editForm.title}
                    onChange={(e) => setEditForm({ ...editForm, title: e.target.value })}
                    className="input-field"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">作者</label>
                  <input
                    type="text"
                    value={editForm.author}
                    onChange={(e) => setEditForm({ ...editForm, author: e.target.value })}
                    className="input-field"
                  />
                </div>
              </div>
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-1">描述</label>
                <textarea
                  value={editForm.description}
                  onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                  className="input-field"
                  rows={2}
                />
              </div>
              <div className="flex space-x-2">
                <button type="submit" disabled={saving} className="btn-primary text-sm">
                  {saving ? '保存中...' : '保存'}
                </button>
                <button type="button" onClick={() => setEditing(false)} className="btn-secondary text-sm">
                  取消
                </button>
              </div>
            </form>
          ) : (
            <div className="flex items-start justify-between">
              <div>
                <h1 className="text-2xl font-bold text-gray-900">{project.title}</h1>
                {project.author && (
                  <p className="mt-1 text-sm text-gray-500">作者: {project.author}</p>
                )}
                {project.description && (
                  <p className="mt-2 text-sm text-gray-600">{project.description}</p>
                )}
                <div className="mt-3 flex items-center space-x-6 text-sm text-gray-500">
                  <span>{project.chapterCount || 0} 章节</span>
                  <span>{(project.totalWords || 0).toLocaleString()} 字</span>
                  <span>更新于 {formatDate(project.updatedAt)}</span>
                </div>
              </div>
              <button
                onClick={() => setEditing(true)}
                className="btn-secondary text-sm flex-shrink-0 ml-4"
              >
                <svg className="w-4 h-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
                编辑
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200 mb-6">
        <nav className="flex space-x-8">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`pb-3 text-sm font-medium border-b-2 transition-colors ${
                activeTab === tab.key
                  ? 'border-primary-500 text-primary-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      {/* Tab Content: Chapters */}
      {activeTab === 'chapters' && (
        <div>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-900">
              章节列表 ({chapters.length})
            </h2>
            <div className="flex items-center space-x-2">
              <button
                onClick={() => { setShowBatchImport(true); setShowAddChapter(false); }}
                className="btn-secondary text-sm"
              >
                <svg className="w-4 h-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
                </svg>
                批量导入
              </button>
              <button
                onClick={() => { setShowAddChapter(true); setShowBatchImport(false); }}
                className="btn-primary text-sm"
              >
                <svg className="w-4 h-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
                </svg>
                添加章节
              </button>
            </div>
          </div>

          {/* Add chapter form */}
          {showAddChapter && (
            <div className="card mb-4 p-4">
              <h3 className="text-sm font-semibold text-gray-900 mb-3">添加新章节</h3>
              <form onSubmit={handleAddChapter} className="space-y-3">
                <input
                  type="text"
                  value={chapterForm.title}
                  onChange={(e) => setChapterForm({ ...chapterForm, title: e.target.value })}
                  className="input-field"
                  placeholder="章节标题"
                />
                <textarea
                  value={chapterForm.content}
                  onChange={(e) => setChapterForm({ ...chapterForm, content: e.target.value })}
                  className="input-field min-h-[120px] resize-y"
                  placeholder="粘贴章节内容..."
                  rows={6}
                />
                <div className="flex space-x-2">
                  <button type="submit" disabled={saving || !chapterForm.title.trim()} className="btn-primary text-sm">
                    {saving ? '保存中...' : '添加'}
                  </button>
                  <button type="button" onClick={() => setShowAddChapter(false)} className="btn-secondary text-sm">
                    取消
                  </button>
                </div>
              </form>
            </div>
          )}

          {/* Batch import form */}
          {showBatchImport && (
            <div className="card mb-4 p-4">
              <h3 className="text-sm font-semibold text-gray-900 mb-2">批量导入</h3>
              <p className="text-xs text-gray-500 mb-3">
                粘贴完整小说文本，系统将自动按章节分隔。支持"第X章"等常见章节标记。
              </p>
              <textarea
                value={batchText}
                onChange={(e) => setBatchText(e.target.value)}
                className="input-field min-h-[200px] resize-y font-mono text-sm"
                placeholder="粘贴完整小说文本..."
                rows={10}
              />
              <div className="flex space-x-2 mt-3">
                <button
                  onClick={handleBatchImport}
                  disabled={saving || !batchText.trim()}
                  className="btn-primary text-sm"
                >
                  {saving ? '导入中...' : '开始导入'}
                </button>
                <button type="button" onClick={() => setShowBatchImport(false)} className="btn-secondary text-sm">
                  取消
                </button>
              </div>
            </div>
          )}

          <ChapterList
            chapters={chapters}
            onDelete={handleDeleteChapter}
            deleting={deletingChapter}
          />
        </div>
      )}

      {/* Tab Content: Analysis */}
      {activeTab === 'analysis' && (
        <div>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-900">小说整体分析</h2>
            <button
              onClick={handleGenerateScript}
              disabled={generating || chapters.length === 0}
              className="btn-primary text-sm"
            >
              {generating ? (
                <span className="flex items-center space-x-1.5">
                  <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  <span>分析中...</span>
                </span>
              ) : (
                <>
                  <svg className="w-4 h-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                  开始分析
                </>
              )}
            </button>
          </div>

          {generating && (
            <div className="card p-8 text-center">
              <svg className="w-10 h-10 animate-spin text-primary-500 mx-auto mb-3" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              <p className="text-gray-600 font-medium">正在分析文本并生成剧本...</p>
              <p className="text-sm text-gray-500 mt-1">这可能需要一些时间，请耐心等待</p>
            </div>
          )}

          {!generating && !analysis && (
            <div className="card p-8 text-center text-gray-500">
              <svg className="mx-auto w-12 h-12 text-gray-300 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
              </svg>
              <p>尚未进行文本分析</p>
              <p className="text-sm mt-1">点击"开始分析"按钮，AI 将分析小说内容并提取角色、场景和章节摘要</p>
            </div>
          )}

          {!generating && analysis && (
            <div className="space-y-4">
              {/* Per-chapter analysis */}
              {analysis.perChapterAnalysis && analysis.perChapterAnalysis.length > 0 ? (
                analysis.perChapterAnalysis.map((chAnalysis) => {
                  const isExpanded = expandedChapters.has(chAnalysis.chapter);
                  return (
                    <div key={chAnalysis.chapter} className="card overflow-hidden">
                      <button
                        className="w-full p-4 flex items-center justify-between text-left hover:bg-gray-50 transition-colors"
                        onClick={() => {
                          setExpandedChapters((prev) => {
                            const next = new Set(prev);
                            if (next.has(chAnalysis.chapter)) {
                              next.delete(chAnalysis.chapter);
                            } else {
                              next.add(chAnalysis.chapter);
                            }
                            return next;
                          });
                        }}
                      >
                        <div className="flex items-center space-x-3">
                          <span className="w-8 h-8 bg-purple-50 text-purple-600 rounded-lg flex items-center justify-center text-sm font-bold">
                            {chAnalysis.chapter}
                          </span>
                          <div>
                            <h4 className="text-sm font-semibold text-gray-900">第 {chAnalysis.chapter} 章</h4>
                            <div className="flex items-center space-x-3 mt-0.5">
                              {chAnalysis.newCharacters && chAnalysis.newCharacters.length > 0 && (
                                <span className="text-xs text-gray-500">
                                  {chAnalysis.newCharacters.length} 位新角色
                                </span>
                              )}
                              {chAnalysis.newLocations && chAnalysis.newLocations.length > 0 && (
                                <span className="text-xs text-gray-500">
                                  {chAnalysis.newLocations.length} 个新场景
                                </span>
                              )}
                            </div>
                          </div>
                        </div>
                        <svg className={`w-5 h-5 text-gray-400 transition-transform ${isExpanded ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                        </svg>
                      </button>

                      {isExpanded && (
                        <div className="px-4 pb-4 border-t border-gray-100">
                          {/* Summary */}
                          {chAnalysis.summary && (
                            <div className="mt-3 mb-3">
                              <h5 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">摘要</h5>
                              <p className="text-sm text-gray-700 leading-relaxed">{chAnalysis.summary}</p>
                            </div>
                          )}

                          {/* New Characters */}
                          {chAnalysis.newCharacters && chAnalysis.newCharacters.length > 0 && (
                            <div className="mt-3">
                              <h5 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2 flex items-center">
                                <svg className="w-3.5 h-3.5 mr-1 text-primary-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
                                </svg>
                                新角色
                              </h5>
                              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                                {chAnalysis.newCharacters.map((char, i) => (
                                  <div key={i} className="p-2.5 bg-primary-50 rounded-lg">
                                    <div className="flex items-center gap-2">
                                      <span className="w-6 h-6 rounded-full bg-primary-100 text-primary-700 flex items-center justify-center text-xs font-bold">
                                        {char.name.charAt(0)}
                                      </span>
                                      <span className="text-sm font-medium text-gray-900">{char.name}</span>
                                      {char.role && (
                                        <span className="text-xs bg-primary-100 text-primary-700 px-1.5 py-0.5 rounded">
                                          {char.role}
                                        </span>
                                      )}
                                    </div>
                                    {char.description && (
                                      <p className="text-xs text-gray-500 mt-1 ml-8">{char.description}</p>
                                    )}
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}

                          {/* New Locations */}
                          {chAnalysis.newLocations && chAnalysis.newLocations.length > 0 && (
                            <div className="mt-3">
                              <h5 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2 flex items-center">
                                <svg className="w-3.5 h-3.5 mr-1 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                                </svg>
                                新场景
                              </h5>
                              <div className="flex flex-wrap gap-2">
                                {chAnalysis.newLocations.map((loc, i) => (
                                  <div key={i} className="px-3 py-2 bg-amber-50 rounded-lg">
                                    <span className="text-sm font-medium text-gray-900">{loc.name}</span>
                                    {loc.description && (
                                      <span className="text-xs text-gray-500 block mt-0.5">{loc.description}</span>
                                    )}
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })
              ) : (
                /* Fallback: use chapterSummaries to build per-chapter cards */
                <>
                  {analysis.chapterSummaries && analysis.chapterSummaries.length > 0 && (
                    analysis.chapterSummaries.map((summary) => {
                      const isExpanded = expandedChapters.has(summary.chapter);
                      return (
                        <div key={summary.chapter} className="card overflow-hidden">
                          <button
                            className="w-full p-4 flex items-center justify-between text-left hover:bg-gray-50 transition-colors"
                            onClick={() => {
                              setExpandedChapters((prev) => {
                                const next = new Set(prev);
                                if (next.has(summary.chapter)) {
                                  next.delete(summary.chapter);
                                } else {
                                  next.add(summary.chapter);
                                }
                                return next;
                              });
                            }}
                          >
                            <div className="flex items-center space-x-3">
                              <span className="w-8 h-8 bg-purple-50 text-purple-600 rounded-lg flex items-center justify-center text-sm font-bold">
                                {summary.chapter}
                              </span>
                              <h4 className="text-sm font-semibold text-gray-900">第 {summary.chapter} 章</h4>
                            </div>
                            <svg className={`w-5 h-5 text-gray-400 transition-transform ${isExpanded ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                              <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                            </svg>
                          </button>

                          {isExpanded && (
                            <div className="px-4 pb-4 border-t border-gray-100">
                              {/* Summary */}
                              <div className="mt-3">
                                <h5 className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-1.5">摘要</h5>
                                <p className="text-sm text-gray-700 leading-relaxed">{summary.summary}</p>
                              </div>

                              {/* Placeholder for characters and locations */}
                              <div className="mt-3 p-3 bg-gray-50 rounded-lg">
                                <p className="text-xs text-gray-400 text-center">
                                  重新生成剧本后，此处将显示本章新出现的角色和场景
                                </p>
                              </div>
                            </div>
                          )}
                        </div>
                      );
                    })
                  )}
                </>
              )}
            </div>
          )}
        </div>
      )}

      {/* Tab Content: Scripts */}
      {activeTab === 'scripts' && (
        <div>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-900">
              剧本版本 ({scripts.length})
            </h2>
            <button
              onClick={handleGenerateScript}
              disabled={generating || chapters.length === 0}
              className="btn-primary text-sm"
            >
              {generating ? (
                <span className="flex items-center space-x-1.5">
                  <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  <span>生成中...</span>
                </span>
              ) : (
                <>
                  <svg className="w-4 h-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
                  </svg>
                  生成剧本
                </>
              )}
            </button>
          </div>

          {generating && (
            <div className="card p-8 text-center mb-4">
              <svg className="w-10 h-10 animate-spin text-primary-500 mx-auto mb-3" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              <p className="text-gray-600 font-medium">正在生成剧本...</p>
              <p className="text-sm text-gray-500 mt-1">AI 正在将小说内容转化为剧本格式</p>
            </div>
          )}

          {scripts.length === 0 && !generating && (
            <div className="card p-8 text-center text-gray-500">
              <svg className="mx-auto w-12 h-12 text-gray-300 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
              </svg>
              <p>还没有生成过剧本</p>
              <p className="text-sm mt-1">点击"生成剧本"按钮，AI 将自动将小说转化为剧本格式</p>
            </div>
          )}

          {scripts.length > 0 && (
            <div className="space-y-2">
              {scripts.map((script) => (
                <div
                  key={script.id}
                  className="card hover:border-primary-200 transition-colors cursor-pointer"
                  onClick={() => navigate(`/project/${projectId}/script/${script.id}`)}
                >
                  <div className="p-4 flex items-center justify-between">
                    <div className="flex items-center space-x-3">
                      <span className="w-8 h-8 bg-primary-50 text-primary-600 rounded-lg flex items-center justify-center text-xs font-medium">
                        {extractChapterBadge(script.title)}
                      </span>
                      <div>
                        <h4 className="text-sm font-medium text-gray-900">{script.title || `剧本 v${script.version}`}</h4>
                        <p className="text-xs text-gray-500">{formatDate(script.createdAt)}</p>
                      </div>
                    </div>
                    <div className="flex items-center space-x-2">
                      <button
                        onClick={(e) => handleDeleteScript(script.id, e)}
                        disabled={deletingScript === script.id}
                        className="p-1.5 text-gray-400 hover:text-red-500 transition-colors disabled:opacity-50"
                        title="删除剧本"
                      >
                        {deletingScript === script.id ? (
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
                      <svg className="w-5 h-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                      </svg>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Chapter Select Modal */}
      {showChapterSelect && (
        <ChapterSelectModal
          chapters={chapters}
          analyzedChapters={analysis?.analyzedChapters || []}
          onConfirm={handleIncrementalGenerate}
          onCancel={() => setShowChapterSelect(false)}
          loading={generating}
        />
      )}
    </div>
  );
}
