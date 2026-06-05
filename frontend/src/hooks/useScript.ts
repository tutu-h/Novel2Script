import { useState, useCallback } from 'react';
import type { Script, ValidationResult } from '../types';
import * as scriptService from '../services/scriptService';

export function useScript() {
  const [script, setScript] = useState<Script | null>(null);
  const [scripts, setScripts] = useState<Script[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [validationResult, setValidationResult] = useState<ValidationResult | null>(null);
  const [isValidating, setIsValidating] = useState(false);
  const [isFixing, setIsFixing] = useState(false);

  const fetchScript = useCallback(async (id: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await scriptService.getScript(id);
      setScript(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '获取剧本失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchProjectScripts = useCallback(async (projectId: number) => {
    setLoading(true);
    setError(null);
    try {
      const data = await scriptService.getProjectScripts(projectId);
      setScripts(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '获取剧本列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const generateScript = useCallback(async (projectId: number): Promise<Script | null> => {
    setIsGenerating(true);
    setError(null);
    try {
      const data = await scriptService.generateScript({ projectId });
      setScript(data);
      setScripts((prev) => [data, ...prev]);
      return data;
    } catch (err) {
      setError(err instanceof Error ? err.message : '生成剧本失败');
      return null;
    } finally {
      setIsGenerating(false);
    }
  }, []);

  const validateScriptAction = useCallback(async (id: number): Promise<ValidationResult | null> => {
    setIsValidating(true);
    setError(null);
    try {
      const result = await scriptService.validateScript(id);
      setValidationResult(result);
      setScript((prev) => prev ? { ...prev, validationResult: result } : null);
      return result;
    } catch (err) {
      setError(err instanceof Error ? err.message : '校验剧本失败');
      return null;
    } finally {
      setIsValidating(false);
    }
  }, []);

  const fixScriptAction = useCallback(async (id: number): Promise<Script | null> => {
    setIsFixing(true);
    setError(null);
    try {
      const data = await scriptService.fixScript(id);
      setScript(data);
      return data;
    } catch (err) {
      setError(err instanceof Error ? err.message : '自动修复失败');
      return null;
    } finally {
      setIsFixing(false);
    }
  }, []);

  const updateScript = useCallback(async (id: number, contentYaml: string): Promise<Script | null> => {
    setLoading(true);
    setError(null);
    try {
      const data = await scriptService.updateScript(id, { contentYaml });
      setScript(data);
      return data;
    } catch (err) {
      setError(err instanceof Error ? err.message : '更新剧本失败');
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  return {
    script,
    scripts,
    loading,
    error,
    isGenerating,
    validationResult,
    isValidating,
    isFixing,
    fetchScript,
    fetchProjectScripts,
    generateScript,
    validateScript: validateScriptAction,
    fixScript: fixScriptAction,
    updateScript,
    setScript,
    setValidationResult,
  };
}
