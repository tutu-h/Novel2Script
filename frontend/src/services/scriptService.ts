import api from './api';
import type { Script, Analysis, GenerateScriptRequest, UpdateScriptRequest, ValidationResult } from '../types';

export async function generateScript(data: GenerateScriptRequest): Promise<Script> {
  const response = await api.post<Script>('/scripts/generate', data);
  return response.data;
}

export async function getProjectScripts(projectId: number): Promise<Script[]> {
  const response = await api.get<Script[]>(`/scripts/project/${projectId}`);
  return response.data;
}

export async function getScript(id: number): Promise<Script> {
  const response = await api.get<Script>(`/scripts/${id}`);
  return response.data;
}

export async function updateScript(id: number, data: UpdateScriptRequest): Promise<Script> {
  const response = await api.put<Script>(`/scripts/${id}`, data);
  return response.data;
}

export async function validateScript(id: number): Promise<ValidationResult> {
  const response = await api.post<ValidationResult>(`/scripts/${id}/validate`);
  return response.data;
}

export async function fixScript(id: number): Promise<Script> {
  const response = await api.post<Script>(`/scripts/${id}/fix`);
  return response.data;
}

export async function getAnalysis(projectId: number): Promise<Analysis> {
  const response = await api.get<Analysis>(`/scripts/project/${projectId}/analysis`);
  return response.data;
}
