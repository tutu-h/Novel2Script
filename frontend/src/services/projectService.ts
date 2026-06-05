import api from './api';
import type {
  Project,
  Chapter,
  CreateProjectRequest,
  UpdateProjectRequest,
  AddChapterRequest,
  BatchAddChaptersRequest,
} from '../types';

export async function createProject(data: CreateProjectRequest): Promise<Project> {
  const response = await api.post<Project>('/projects', data);
  return response.data;
}

export async function getProjects(): Promise<Project[]> {
  const response = await api.get<Project[]>('/projects');
  return response.data;
}

export async function getProject(id: number): Promise<Project> {
  const response = await api.get<Project>(`/projects/${id}`);
  return response.data;
}

export async function updateProject(id: number, data: UpdateProjectRequest): Promise<Project> {
  const response = await api.put<Project>(`/projects/${id}`, data);
  return response.data;
}

export async function deleteProject(id: number): Promise<void> {
  await api.delete(`/projects/${id}`);
}

export async function addChapter(projectId: number, data: AddChapterRequest): Promise<Chapter> {
  const response = await api.post<Chapter>(`/projects/${projectId}/chapters`, data);
  return response.data;
}

export async function getChapters(projectId: number): Promise<Chapter[]> {
  const response = await api.get<Chapter[]>(`/projects/${projectId}/chapters`);
  return response.data;
}

export async function batchAddChapters(projectId: number, data: BatchAddChaptersRequest): Promise<Chapter[]> {
  const response = await api.post<Chapter[]>(`/projects/${projectId}/chapters/batch`, data);
  return response.data;
}

export async function deleteChapter(projectId: number, chapterNumber: number): Promise<void> {
  await api.delete(`/projects/${projectId}/chapters/${chapterNumber}`);
}
