export interface Project {
  id: number;
  title: string;
  author: string;
  description: string;
  createdAt: string;
  updatedAt: string;
  chapterCount: number;
  totalWords: number;
  chapters?: Chapter[];
}

export interface Chapter {
  id: number;
  projectId: number;
  chapterNumber: number;
  title: string;
  content: string;
  wordCount: number;
  createdAt: string;
}

export interface Analysis {
  id: number;
  projectId: number;
  characters: AnalysisCharacter[];
  locations: AnalysisLocation[];
  events: AnalysisEvent[];
  chapterSummaries: ChapterSummary[];
  createdAt: string;
}

export interface AnalysisCharacter {
  name: string;
  description: string;
  role: string;
}

export interface AnalysisLocation {
  name: string;
  description: string;
}

export interface AnalysisEvent {
  title: string;
  description: string;
  chapter: number;
}

export interface ChapterSummary {
  chapterNumber: number;
  summary: string;
}

export interface Script {
  id: number;
  projectId: number;
  version: number;
  title: string;
  contentYaml: string;
  validationResult?: ValidationResult;
  createdAt: string;
  updatedAt: string;
}

export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
}

export interface ValidationError {
  path: string;
  message: string;
}

export interface ValidationWarning {
  path: string;
  message: string;
}

export interface CreateProjectRequest {
  title: string;
  author: string;
  description: string;
}

export interface UpdateProjectRequest {
  title?: string;
  author?: string;
  description?: string;
}

export interface AddChapterRequest {
  title: string;
  content: string;
}

export interface BatchAddChaptersRequest {
  text: string;
}

export interface GenerateScriptRequest {
  projectId: number;
}

export interface UpdateScriptRequest {
  contentYaml: string;
}

export interface ExportRequest {
  scriptId: number;
  format: 'yaml' | 'markdown' | 'pdf';
}
