import { useState, useEffect, useCallback } from 'react';
import { AiModelConfig, AiModelConfigRequest, ProviderOption } from '../types';
import { aiModelService } from '../services/aiModelService';

const PROVIDERS: ProviderOption[] = [
  { value: 'deepseek', label: 'DeepSeek', defaultUrl: 'https://api.deepseek.com' },
  { value: 'qwen', label: '通义千问', defaultUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { value: 'openai', label: 'OpenAI', defaultUrl: 'https://api.openai.com/v1' },
  { value: 'zhipu', label: '智谱清言', defaultUrl: 'https://open.bigmodel.cn/api/paas/v4' },
  { value: 'moonshot', label: '月之暗面', defaultUrl: 'https://api.moonshot.cn/v1' },
];

const PROVIDER_COLORS: Record<string, { bg: string; text: string; border: string }> = {
  deepseek: { bg: 'bg-blue-100', text: 'text-blue-700', border: 'border-blue-300' },
  qwen: { bg: 'bg-orange-100', text: 'text-orange-700', border: 'border-orange-300' },
  openai: { bg: 'bg-green-100', text: 'text-green-700', border: 'border-green-300' },
  zhipu: { bg: 'bg-purple-100', text: 'text-purple-700', border: 'border-purple-300' },
  moonshot: { bg: 'bg-indigo-100', text: 'text-indigo-700', border: 'border-indigo-300' },
};

interface AiModelSettingsProps {
  open: boolean;
  onClose: () => void;
}

export default function AiModelSettings({ open, onClose }: AiModelSettingsProps) {
  const [models, setModels] = useState<AiModelConfig[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedProvider, setSelectedProvider] = useState<string | null>(null);
  const [formData, setFormData] = useState<AiModelConfigRequest>({
    provider: '',
    modelName: '',
    apiKey: '',
    baseUrl: '',
  });
  const [showApiKey, setShowApiKey] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [testingId, setTestingId] = useState<number | null>(null);
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);

  const fetchModels = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await aiModelService.getModels();
      setModels(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载模型列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open) {
      fetchModels();
    }
  }, [open, fetchModels]);

  const handleProviderSelect = (provider: ProviderOption) => {
    setSelectedProvider(provider.value);
    setFormData({
      provider: provider.value,
      modelName: '',
      apiKey: '',
      baseUrl: provider.defaultUrl,
    });
    setShowApiKey(false);
  };

  const handleAddModel = async () => {
    if (!formData.modelName.trim() || !formData.apiKey.trim()) {
      return;
    }
    try {
      setSubmitting(true);
      setError(null);
      await aiModelService.createModel(formData);
      setSelectedProvider(null);
      setFormData({ provider: '', modelName: '', apiKey: '', baseUrl: '' });
      await fetchModels();
    } catch (err) {
      setError(err instanceof Error ? err.message : '添加模型失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggle = async (id: number) => {
    try {
      setError(null);
      await aiModelService.toggleModel(id);
      await fetchModels();
    } catch (err) {
      setError(err instanceof Error ? err.message : '切换模型状态失败');
    }
  };

  const handleActivate = async (id: number) => {
    try {
      setError(null);
      await aiModelService.activateModel(id);
      await fetchModels();
    } catch (err) {
      setError(err instanceof Error ? err.message : '激活模型失败');
    }
  };

  const handleTest = async (id: number) => {
    try {
      setTestingId(id);
      setError(null);
      await aiModelService.testModel(id);
      await fetchModels();
    } catch (err) {
      setError(err instanceof Error ? err.message : '测试模型失败');
    } finally {
      setTestingId(null);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      setError(null);
      await aiModelService.deleteModel(id);
      setDeleteConfirmId(null);
      await fetchModels();
    } catch (err) {
      setError(err instanceof Error ? err.message : '删除模型失败');
    }
  };

  const getProviderLabel = (value: string) => {
    return PROVIDERS.find((p) => p.value === value)?.label || value;
  };

  const formatTime = (dateStr: string | null) => {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleString('zh-CN');
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[100] flex items-start justify-center overflow-y-auto">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/50 backdrop-blur-sm animate-fade-in transition-opacity"
        onClick={onClose}
      />

      {/* Modal */}
      <div className="relative w-full max-w-3xl mx-4 my-8 bg-white rounded-2xl shadow-2xl animate-slide-up">
        {/* Header */}
        <div className="sticky top-0 z-10 flex items-center justify-between px-6 py-4 bg-white border-b border-gray-100 rounded-t-2xl">
          <h2 className="text-xl font-bold text-gray-900">AI 模型配置</h2>
          <button
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Content */}
        <div className="px-6 py-5 space-y-6">
          {/* Error message */}
          {error && (
            <div className="p-4 bg-red-50 border border-red-200 rounded-lg flex items-start gap-3">
              <svg className="w-5 h-5 text-red-500 flex-shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L3.34 16.5c-.77.833.192 2.5 1.732 2.5z" />
              </svg>
              <p className="text-sm text-red-700">{error}</p>
              <button onClick={() => setError(null)} className="ml-auto text-red-400 hover:text-red-600">
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          )}

          {/* Add model section */}
          <section>
            <h3 className="text-sm font-semibold text-gray-700 mb-3">添加新模型</h3>

            {/* Provider cards */}
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3">
              {PROVIDERS.map((provider) => {
                const colors = PROVIDER_COLORS[provider.value];
                const isSelected = selectedProvider === provider.value;
                return (
                  <button
                    key={provider.value}
                    onClick={() => handleProviderSelect(provider)}
                    className={`p-3 rounded-xl border-2 text-center transition-all duration-200 ${
                      isSelected
                        ? `${colors.bg} ${colors.border} ${colors.text} shadow-sm`
                        : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
                    }`}
                  >
                    <div className={`w-10 h-10 mx-auto mb-2 rounded-lg flex items-center justify-center text-lg font-bold ${
                      isSelected ? colors.bg : 'bg-gray-100'
                    }`}>
                      {provider.label.charAt(0)}
                    </div>
                    <div className="text-sm font-medium truncate">{provider.label}</div>
                  </button>
                );
              })}
            </div>

            {/* Add form */}
            {selectedProvider && (
              <div className="mt-4 p-4 bg-gray-50 rounded-xl border border-gray-200 space-y-4 animate-slide-down">
                <div className="text-sm font-medium text-gray-700">
                  添加 {getProviderLabel(selectedProvider)} 模型
                </div>

                <div>
                  <label className="block text-sm text-gray-600 mb-1">模型名称</label>
                  <input
                    type="text"
                    value={formData.modelName}
                    onChange={(e) => setFormData({ ...formData, modelName: e.target.value })}
                    placeholder="例如: deepseek-chat, gpt-4o"
                    className="w-full px-3 py-2 bg-white border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-shadow"
                  />
                </div>

                <div>
                  <label className="block text-sm text-gray-600 mb-1">API 密钥</label>
                  <div className="relative">
                    <input
                      type={showApiKey ? 'text' : 'password'}
                      value={formData.apiKey}
                      onChange={(e) => setFormData({ ...formData, apiKey: e.target.value })}
                      placeholder="输入 API Key"
                      className="w-full px-3 py-2 pr-10 bg-white border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-shadow"
                    />
                    <button
                      type="button"
                      onClick={() => setShowApiKey(!showApiKey)}
                      className="absolute right-2 top-1/2 -translate-y-1/2 p-1 text-gray-400 hover:text-gray-600"
                    >
                      {showApiKey ? (
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
                        </svg>
                      ) : (
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                          <path strokeLinecap="round" strokeLinejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                        </svg>
                      )}
                    </button>
                  </div>
                </div>

                <div>
                  <label className="block text-sm text-gray-600 mb-1">
                    Base URL <span className="text-gray-400">(可选，已预填默认值)</span>
                  </label>
                  <input
                    type="text"
                    value={formData.baseUrl || ''}
                    onChange={(e) => setFormData({ ...formData, baseUrl: e.target.value })}
                    placeholder="API 地址"
                    className="w-full px-3 py-2 bg-white border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-shadow"
                  />
                </div>

                <div className="flex gap-3">
                  <button
                    onClick={handleAddModel}
                    disabled={submitting || !formData.modelName.trim() || !formData.apiKey.trim()}
                    className="px-4 py-2 bg-primary-600 text-white text-sm font-medium rounded-lg hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
                  >
                    {submitting && (
                      <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                    )}
                    添加
                  </button>
                  <button
                    onClick={() => setSelectedProvider(null)}
                    className="px-4 py-2 text-gray-600 text-sm font-medium rounded-lg hover:bg-gray-200 transition-colors"
                  >
                    取消
                  </button>
                </div>
              </div>
            )}
          </section>

          {/* Configured models section */}
          <section>
            <h3 className="text-sm font-semibold text-gray-700 mb-3">
              已配置模型 {models.length > 0 && <span className="text-gray-400 font-normal">({models.length})</span>}
            </h3>

            {loading ? (
              <div className="flex items-center justify-center py-12">
                <svg className="w-8 h-8 animate-spin text-primary-500" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
              </div>
            ) : models.length === 0 ? (
              <div className="text-center py-12 bg-gray-50 rounded-xl border border-dashed border-gray-300">
                <svg className="w-12 h-12 mx-auto text-gray-300 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
                </svg>
                <p className="text-gray-500 text-sm">暂无已配置的模型</p>
                <p className="text-gray-400 text-xs mt-1">请从上方选择服务商并添加模型</p>
              </div>
            ) : (
              <div className="space-y-3">
                {models.map((model) => {
                  const colors = PROVIDER_COLORS[model.provider] || PROVIDER_COLORS.deepseek;
                  const isTesting = testingId === model.id;
                  const isDeleteConfirm = deleteConfirmId === model.id;

                  return (
                    <div
                      key={model.id}
                      className={`p-4 bg-white rounded-xl border transition-all duration-200 ${
                        model.active ? 'border-green-300 shadow-sm shadow-green-100' : 'border-gray-200 hover:border-gray-300'
                      }`}
                    >
                      <div className="flex items-start gap-4">
                        {/* Model info */}
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 flex-wrap mb-1">
                            <span className={`inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-md ${colors.bg} ${colors.text}`}>
                              {getProviderLabel(model.provider)}
                            </span>
                            <span className="text-sm font-bold text-gray-900 truncate">{model.modelName}</span>
                            {model.active && (
                              <span className="inline-flex items-center px-2 py-0.5 text-xs font-semibold rounded-full bg-green-100 text-green-700 border border-green-300">
                                当前使用
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-3 text-xs text-gray-500">
                            <span className="font-mono">{model.apiKeyMasked}</span>
                          </div>
                          <div className="text-xs text-gray-400 mt-0.5 truncate">{model.baseUrl}</div>
                          {model.lastTestAt && (
                            <div className="flex items-center gap-1 mt-1 text-xs">
                              {model.lastTestStatus === 'success' ? (
                                <svg className="w-3.5 h-3.5 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                                </svg>
                              ) : (
                                <svg className="w-3.5 h-3.5 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                                </svg>
                              )}
                              <span className={model.lastTestStatus === 'success' ? 'text-green-600' : 'text-red-600'}>
                                {model.lastTestStatus === 'success' ? '连接正常' : '连接失败'}
                              </span>
                              <span className="text-gray-400">- {formatTime(model.lastTestAt)}</span>
                            </div>
                          )}
                        </div>

                        {/* Actions */}
                        <div className="flex items-center gap-2 flex-shrink-0">
                          {/* Toggle */}
                          <button
                            onClick={() => handleToggle(model.id)}
                            className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 ${
                              model.enabled ? 'bg-primary-600' : 'bg-gray-300'
                            }`}
                            title={model.enabled ? '已启用' : '已禁用'}
                          >
                            <span
                              className={`inline-block h-4 w-4 transform rounded-full bg-white shadow-sm transition-transform duration-200 ${
                                model.enabled ? 'translate-x-6' : 'translate-x-1'
                              }`}
                            />
                          </button>

                          {/* Activate */}
                          {!model.active && model.enabled && (
                            <button
                              onClick={() => handleActivate(model.id)}
                              className="px-3 py-1.5 text-xs font-medium text-primary-700 bg-primary-50 hover:bg-primary-100 rounded-lg transition-colors"
                            >
                              设为使用
                            </button>
                          )}

                          {/* Test */}
                          <button
                            onClick={() => handleTest(model.id)}
                            disabled={isTesting || !model.enabled}
                            className="p-1.5 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                            title="测试连接"
                          >
                            {isTesting ? (
                              <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24">
                                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                              </svg>
                            ) : (
                              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
                              </svg>
                            )}
                          </button>

                          {/* Delete */}
                          {isDeleteConfirm ? (
                            <div className="flex items-center gap-1">
                              <button
                                onClick={() => handleDelete(model.id)}
                                className="px-2 py-1 text-xs font-medium text-white bg-red-600 hover:bg-red-700 rounded-lg transition-colors"
                              >
                                确认删除
                              </button>
                              <button
                                onClick={() => setDeleteConfirmId(null)}
                                className="px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
                              >
                                取消
                              </button>
                            </div>
                          ) : (
                            <button
                              onClick={() => setDeleteConfirmId(model.id)}
                              className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                              title="删除模型"
                            >
                              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                <path strokeLinecap="round" strokeLinejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                              </svg>
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
