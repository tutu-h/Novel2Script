import { useMemo, useState } from 'react';
import yaml from 'js-yaml';

interface ScriptViewerProps {
  contentYaml: string;
}

// --- Types matching our actual YAML schema ---

interface SourceMapping {
  source_chapter?: number;
  source_range?: string;
  confidence?: string;
}

interface Dialogue {
  speaker: string;
  text: string;
}

interface Beat {
  beat_id: number;
  description?: string;
  dialogues?: Dialogue[];
  stage_directions?: string[];
  source_mapping?: SourceMapping;
}

interface Scene {
  scene_id: number;
  setting?: string;
  characters?: string[];
  beats?: Beat[];
  mood?: string;
  transition?: string;
  source_chapter?: number;
}

interface Act {
  act_id: number;
  title?: string;
  scenes?: Scene[];
  source_chapters?: number[];
}

interface Character {
  name: string;
  description?: string;
  role_type?: string;
}

interface Location {
  name: string;
  description?: string;
}

interface Metadata {
  title?: string;
  author?: string;
  source_chapters?: number[];
}

interface ParsedScript {
  metadata?: Metadata;
  characters?: Character[];
  locations?: Location[];
  acts?: Act[];
}

// --- Parser ---

function parseScript(contentYaml: string): ParsedScript | null {
  try {
    const parsed = yaml.load(contentYaml) as Record<string, unknown>;
    if (!parsed || typeof parsed !== 'object') return null;
    // Support both root-level and nested under "script"
    const script = (parsed.script ?? parsed) as ParsedScript;
    return script;
  } catch {
    return null;
  }
}

// --- Sub-components ---

function roleTypeLabel(roleType: string) {
  const map: Record<string, { label: string; color: string }> = {
    protagonist: { label: '主角', color: 'bg-red-100 text-red-700' },
    antagonist: { label: '反派', color: 'bg-purple-100 text-purple-700' },
    supporting: { label: '配角', color: 'bg-blue-100 text-blue-700' },
    minor: { label: '次要', color: 'bg-gray-100 text-gray-600' },
  };
  const info = map[roleType] || { label: roleType || '未知', color: 'bg-gray-100 text-gray-600' };
  return (
    <span className={`inline-block text-xs px-2 py-0.5 rounded-full font-medium ${info.color}`}>
      {info.label}
    </span>
  );
}

function confidenceBadge(confidence: string) {
  const map: Record<string, string> = {
    high: 'bg-green-100 text-green-700',
    medium: 'bg-yellow-100 text-yellow-700',
    low: 'bg-red-100 text-red-600',
  };
  return (
    <span className={`text-xs px-1.5 py-0.5 rounded ${map[confidence] || 'bg-gray-100 text-gray-500'}`}>
      {confidence === 'high' ? '高匹配' : confidence === 'medium' ? '中匹配' : confidence === 'low' ? '低匹配' : confidence}
    </span>
  );
}

function SourceMappingBlock({ mapping }: { mapping: SourceMapping }) {
  const [expanded, setExpanded] = useState(false);
  if (!mapping.source_range && !mapping.source_chapter) return null;

  return (
    <div className="mt-2">
      <button
        onClick={() => setExpanded(!expanded)}
        className="text-xs text-gray-400 hover:text-gray-600 flex items-center gap-1 transition-colors"
      >
        <svg className={`w-3 h-3 transition-transform ${expanded ? 'rotate-90' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
        原文追溯
        {mapping.confidence && confidenceBadge(mapping.confidence)}
      </button>
      {expanded && (
        <div className="mt-1.5 ml-4 p-2.5 bg-gray-50 border-l-2 border-gray-200 rounded-r text-xs text-gray-500 italic leading-relaxed">
          {mapping.source_chapter && (
            <span className="block text-gray-400 not-italic mb-1">来源: 第 {mapping.source_chapter} 章</span>
          )}
          {mapping.source_range && <span>"{mapping.source_range}"</span>}
        </div>
      )}
    </div>
  );
}

function DialogueBlock({ dialogue }: { dialogue: Dialogue }) {
  return (
    <div className="flex gap-3 mb-3">
      <div className="flex-shrink-0 w-8 h-8 rounded-full bg-primary-50 text-primary-600 flex items-center justify-center text-xs font-bold mt-0.5">
        {dialogue.speaker.charAt(0)}
      </div>
      <div className="flex-1 min-w-0">
        <span className="text-sm font-semibold text-primary-700">{dialogue.speaker}</span>
        <p className="text-sm text-gray-800 mt-0.5 leading-relaxed">"{dialogue.text}"</p>
      </div>
    </div>
  );
}

function BeatBlock({ beat }: { beat: Beat }) {
  return (
    <div className="mb-5 pl-4 border-l-2 border-gray-100 hover:border-primary-200 transition-colors">
      {/* Beat description */}
      {beat.description && (
        <p className="text-sm text-gray-600 leading-relaxed mb-2">{beat.description}</p>
      )}

      {/* Stage directions */}
      {beat.stage_directions && beat.stage_directions.length > 0 && (
        <div className="mb-2">
          {beat.stage_directions.map((dir, i) => (
            <p key={i} className="text-xs text-gray-400 italic leading-relaxed">
              [{dir}]
            </p>
          ))}
        </div>
      )}

      {/* Dialogues */}
      {beat.dialogues && beat.dialogues.length > 0 && (
        <div className="mb-2">
          {beat.dialogues.map((d, i) => (
            <DialogueBlock key={i} dialogue={d} />
          ))}
        </div>
      )}

      {/* Source mapping */}
      {beat.source_mapping && <SourceMappingBlock mapping={beat.source_mapping} />}
    </div>
  );
}

function SceneBlock({ scene, index }: { scene: Scene; index: number }) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="mb-6">
      <div
        className="flex items-center gap-2 cursor-pointer group mb-3"
        onClick={() => setCollapsed(!collapsed)}
      >
        <svg className={`w-4 h-4 text-gray-400 transition-transform ${collapsed ? '' : 'rotate-90'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
        <span className="flex-shrink-0 w-6 h-6 bg-amber-100 text-amber-700 rounded text-xs font-bold flex items-center justify-center">
          {scene.scene_id ?? index + 1}
        </span>
        <h4 className="text-sm font-semibold text-gray-800 group-hover:text-amber-700 transition-colors">
          {scene.setting || `场景 ${scene.scene_id ?? index + 1}`}
        </h4>
        {scene.mood && (
          <span className="text-xs bg-indigo-50 text-indigo-600 px-2 py-0.5 rounded-full">{scene.mood}</span>
        )}
        {scene.source_chapter && (
          <span className="text-xs bg-gray-100 text-gray-500 px-2 py-0.5 rounded-full">
            第{scene.source_chapter}章
          </span>
        )}
      </div>

      {!collapsed && (
        <div className="ml-8">
          {/* Scene characters */}
          {scene.characters && scene.characters.length > 0 && (
            <div className="mb-3 flex flex-wrap gap-1.5">
              <span className="text-xs text-gray-400 mr-1">出场角色:</span>
              {scene.characters.map((char, i) => (
                <span key={i} className="text-xs bg-primary-50 text-primary-600 px-2 py-0.5 rounded-full">{char}</span>
              ))}
            </div>
          )}

          {/* Beats */}
          {scene.beats && scene.beats.length > 0 ? (
            scene.beats.map((beat, i) => <BeatBlock key={i} beat={beat} />)
          ) : (
            <p className="text-xs text-gray-400 italic">此场景暂无节拍数据</p>
          )}

          {/* Transition */}
          {scene.transition && (
            <div className="flex items-center gap-2 mt-4 mb-2">
              <div className="flex-1 h-px bg-gray-200" />
              <span className="text-xs text-gray-400 italic">{scene.transition}</span>
              <div className="flex-1 h-px bg-gray-200" />
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function ActBlock({ act, index }: { act: Act; index: number }) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="mb-8">
      <div
        className="flex items-center gap-2 cursor-pointer group pb-2 border-b border-gray-200 mb-4"
        onClick={() => setCollapsed(!collapsed)}
      >
        <svg className={`w-5 h-5 text-gray-400 transition-transform ${collapsed ? '' : 'rotate-90'}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
        <span className="flex-shrink-0 w-8 h-8 bg-primary-100 text-primary-700 rounded-lg text-sm font-bold flex items-center justify-center">
          {act.act_id ?? index + 1}
        </span>
        <h3 className="text-lg font-bold text-gray-900 group-hover:text-primary-600 transition-colors">
          第 {act.act_id ?? index + 1} 幕
          {act.title && <span className="ml-2 font-normal text-gray-500 text-base">- {act.title}</span>}
        </h3>
        {act.source_chapters && act.source_chapters.length > 0 && (
          <span className="text-xs bg-gray-100 text-gray-500 px-2 py-0.5 rounded-full">
            第{act.source_chapters.join(',')}章
          </span>
        )}
      </div>

      {!collapsed && (
        <div className="ml-4">
          {act.scenes && act.scenes.length > 0 ? (
            act.scenes.map((scene, i) => <SceneBlock key={i} scene={scene} index={i} />)
          ) : (
            <p className="text-sm text-gray-400 italic ml-4">此幕暂无场景数据</p>
          )}
        </div>
      )}
    </div>
  );
}

function CharactersPanel({ characters }: { characters: Character[] }) {
  const [expanded, setExpanded] = useState(false);
  if (characters.length === 0) return null;

  return (
    <div className="mb-6">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex items-center gap-2 text-sm font-semibold text-gray-700 hover:text-primary-600 transition-colors mb-3"
      >
        <svg className="w-4 h-4 text-primary-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
        角色表 ({characters.length})
        <svg className={`w-3.5 h-3.5 text-gray-400 transition-transform ${expanded ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {expanded && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mb-2">
          {characters.map((char, i) => (
            <div key={i} className="p-3 bg-gray-50 rounded-lg">
              <div className="flex items-center gap-2 mb-1">
                <span className="w-7 h-7 rounded-full bg-primary-100 text-primary-700 flex items-center justify-center text-xs font-bold">
                  {char.name.charAt(0)}
                </span>
                <span className="text-sm font-semibold text-gray-900">{char.name}</span>
                {char.role_type && roleTypeLabel(char.role_type)}
              </div>
              {char.description && (
                <p className="text-xs text-gray-500 leading-relaxed mt-1 ml-9">{char.description}</p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function LocationsPanel({ locations }: { locations: Location[] }) {
  const [expanded, setExpanded] = useState(false);
  if (locations.length === 0) return null;

  return (
    <div className="mb-6">
      <button
        onClick={() => setExpanded(!expanded)}
        className="flex items-center gap-2 text-sm font-semibold text-gray-700 hover:text-amber-600 transition-colors mb-3"
      >
        <svg className="w-4 h-4 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
          <path strokeLinecap="round" strokeLinejoin="round" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
        场景地点 ({locations.length})
        <svg className={`w-3.5 h-3.5 text-gray-400 transition-transform ${expanded ? 'rotate-180' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {expanded && (
        <div className="flex flex-wrap gap-2 mb-2">
          {locations.map((loc, i) => (
            <div key={i} className="px-3 py-2 bg-amber-50 rounded-lg">
              <span className="text-sm font-medium text-gray-900">{loc.name}</span>
              {loc.description && (
                <span className="text-xs text-gray-500 block mt-0.5">{loc.description}</span>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// --- Main component ---

export default function ScriptViewer({ contentYaml }: ScriptViewerProps) {
  const script = useMemo(() => parseScript(contentYaml), [contentYaml]);

  if (!script) {
    return (
      <div className="p-8 text-center text-gray-500">
        <svg className="mx-auto w-12 h-12 text-gray-300 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
        </svg>
        <p className="font-medium">无法解析剧本 YAML</p>
        <p className="text-xs mt-1">请使用编辑模式检查 YAML 格式是否正确</p>
      </div>
    );
  }

  const acts = script.acts || [];
  const hasContent = acts.length > 0;

  return (
    <div className="p-4 max-w-4xl">
      {/* Title header */}
      {script.metadata?.title && (
        <div className="mb-6 pb-4 border-b border-gray-200">
          <h2 className="text-2xl font-bold text-gray-900">{script.metadata.title}</h2>
          <div className="flex items-center gap-4 mt-2 text-sm text-gray-500">
            {script.metadata.author && <span>作者: {script.metadata.author}</span>}
            {script.metadata.source_chapters && script.metadata.source_chapters.length > 0 && (
              <span>来源章节: {script.metadata.source_chapters.join(', ')}</span>
            )}
            <span>{acts.length} 幕</span>
          </div>
        </div>
      )}

      {/* Text Analysis for this script */}
      {(script.characters?.length || script.locations?.length) && (
        <div className="mb-6 pb-4 border-b border-gray-200">
          <h3 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">
            文本分析
          </h3>
          {script.characters && script.characters.length > 0 && <CharactersPanel characters={script.characters} />}
          {script.locations && script.locations.length > 0 && <LocationsPanel locations={script.locations} />}
        </div>
      )}

      {/* Acts */}
      {hasContent ? (
        <div>
          {acts.map((act, i) => (
            <ActBlock key={act.act_id ?? i} act={act} index={i} />
          ))}
        </div>
      ) : (
        <div className="p-8 text-center text-gray-400 border border-dashed border-gray-200 rounded-xl">
          <svg className="mx-auto w-10 h-10 text-gray-300 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
          </svg>
          <p className="font-medium">剧本幕场结构为空</p>
          <p className="text-xs mt-1">生成的剧本可能未包含有效的幕/场/节拍结构</p>
        </div>
      )}
    </div>
  );
}
