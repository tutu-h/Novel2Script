import api from './api';
import { AiModelConfig, AiModelConfigRequest } from '../types';

export const aiModelService = {
  getModels: async (): Promise<AiModelConfig[]> => {
    const response = await api.get<AiModelConfig[]>('/ai-models');
    return response.data;
  },

  createModel: async (data: AiModelConfigRequest): Promise<AiModelConfig> => {
    const response = await api.post<AiModelConfig>('/ai-models', data);
    return response.data;
  },

  updateModel: async (id: number, data: AiModelConfigRequest): Promise<AiModelConfig> => {
    const response = await api.put<AiModelConfig>(`/ai-models/${id}`, data);
    return response.data;
  },

  deleteModel: async (id: number): Promise<void> => {
    await api.delete(`/ai-models/${id}`);
  },

  toggleModel: async (id: number): Promise<AiModelConfig> => {
    const response = await api.post<AiModelConfig>(`/ai-models/${id}/toggle`);
    return response.data;
  },

  activateModel: async (id: number): Promise<AiModelConfig> => {
    const response = await api.post<AiModelConfig>(`/ai-models/${id}/activate`);
    return response.data;
  },

  testModel: async (id: number): Promise<AiModelConfig> => {
    const response = await api.post<AiModelConfig>(`/ai-models/${id}/test`);
    return response.data;
  },
};
