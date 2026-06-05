import { useMemo } from 'react';
import yaml from 'js-yaml';

interface ScriptViewerProps {
  contentYaml: string;
}

interface ParsedScript {
  title?: string;
  acts?: Act[];
  [key: string]: unknown;
}

interface Act {
  act?: number | string;
  title?: string;
  scenes?: Scene[];
  [key: string]: unknown;
}

interface Scene {
  scene?: number | string;
  setting?: string;
  heading?: string;
  beats?: Beat[];
  [key: string]: unknown;
}

interface Beat {
  beat?: number | string;
  type?: string;
  description?: string;
  character?: string;
  dialogue?: string;
  direction?: string;
  source?: string;
  items?: BeatItem[];
  [key: string]: unknown;
}

interface BeatItem {
  type?: string;
  character?: string;
  dialogue?: string;
  direction?: string;
  [key: string]: unknown;
}

function tryParseYaml(contentYaml: string): ParsedScript | null {
  try {
    const parsed = yaml.load(contentYaml);
    if (parsed && typeof parsed === 'object') {
      return parsed as ParsedScript;
    }
    return null;
  } catch {
    return null;
  }
}

function SourceBadge({ source }: { source?: string }) {
  if (!source) return null;
  return (
    <span className="inline-block text-xs bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded ml-2">
      {source}
    </span>
  );
}

function BeatRenderer({ beat }: { beat: Beat; index: number }) {
  const beatType = beat.type || 'action';
  const isDialogue = beatType === 'dialogue' || beatType === 'dialog' || !!beat.dialogue || !!beat.character;
  const isDirection = beatType === 'direction' || beatType === 'stage_direction' || !!beat.direction;

  if (beat.items && Array.isArray(beat.items)) {
    return (
      <div className="ml-4 mb-2">
        {beat.description && (
          <p className="text-sm text-gray-600 mb-1">{beat.description}</p>
        )}
        {beat.items.map((item, i) => (
          <BeatRenderer key={i} beat={item as Beat} index={i} />
        ))}
      </div>
    );
  }

  return (
    <div className="ml-4 mb-2 flex items-start">
      <span className="flex-shrink-0 w-1.5 h-1.5 bg-gray-300 rounded-full mt-2 mr-2" />
      <div className="flex-1">
        {isDialogue && (
          <div className="mb-1">
            {beat.character && (
              <span className="inline-block text-sm font-semibold text-primary-600 mr-2">
                {beat.character}
              </span>
            )}
            {beat.dialogue && (
              <span className="text-sm text-gray-800">
                "{beat.dialogue}"
              </span>
            )}
            <SourceBadge source={beat.source as string | undefined} />
          </div>
        )}
        {isDirection && (
          <div className="mb-1">
            <span className="text-sm text-gray-500 italic">
              [{beat.direction || beat.description || ''}]
            </span>
            <SourceBadge source={beat.source as string | undefined} />
          </div>
        )}
        {!isDialogue && !isDirection && (
          <div className="mb-1">
            <span className="text-sm text-gray-700">
              {beat.description || beat.dialogue || ''}
            </span>
            {beat.character && (
              <span className="inline-block text-xs text-primary-500 ml-2">
                ({beat.character})
              </span>
            )}
            <SourceBadge source={beat.source as string | undefined} />
          </div>
        )}
      </div>
    </div>
  );
}

function SceneRenderer({ scene, index }: { scene: Scene; index: number }) {
  return (
    <div className="mb-4">
      <div className="flex items-center mb-2">
        <span className="flex-shrink-0 w-6 h-6 bg-amber-100 text-amber-700 rounded text-xs font-medium flex items-center justify-center mr-2">
          S{scene.scene ?? index + 1}
        </span>
        <h4 className="text-sm font-semibold text-gray-800">
          {String(scene.heading || scene.title || `场景 ${scene.scene ?? index + 1}`)}
        </h4>
      </div>
      {scene.setting && (
        <div className="ml-8 mb-2 px-3 py-1.5 bg-blue-50 rounded-lg inline-block">
          <span className="text-xs text-blue-600 font-medium">场景设定: </span>
          <span className="text-xs text-blue-700">{scene.setting}</span>
        </div>
      )}
      {scene.beats && Array.isArray(scene.beats) && (
        <div className="ml-4">
          {scene.beats.map((beat, i) => (
            <BeatRenderer key={i} beat={beat} index={i} />
          ))}
        </div>
      )}
    </div>
  );
}

function ActRenderer({ act, index }: { act: Act; index: number }) {
  return (
    <details open className="mb-6">
      <summary className="flex items-center cursor-pointer group">
        <svg className="w-4 h-4 text-gray-400 mr-1 transition-transform group-open:rotate-90" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
        </svg>
        <span className="flex-shrink-0 w-7 h-7 bg-primary-100 text-primary-700 rounded text-xs font-bold flex items-center justify-center mr-2">
          {act.act ?? index + 1}
        </span>
        <h3 className="text-base font-bold text-gray-900 group-hover:text-primary-600 transition-colors">
          第 {act.act ?? index + 1} 幕
          {act.title && <span className="ml-2 font-normal text-gray-600">- {act.title}</span>}
        </h3>
      </summary>
      <div className="mt-3 ml-4 pl-4 border-l-2 border-gray-100">
        {act.scenes && Array.isArray(act.scenes) ? (
          act.scenes.map((scene, i) => (
            <SceneRenderer key={i} scene={scene} index={i} />
          ))
        ) : (
          <p className="text-sm text-gray-500 italic">无场景数据</p>
        )}
      </div>
    </details>
  );
}

export default function ScriptViewer({ contentYaml }: ScriptViewerProps) {
  const parsed = useMemo(() => tryParseYaml(contentYaml), [contentYaml]);

  if (!parsed) {
    return (
      <div className="p-6 text-center text-gray-500">
        <svg className="mx-auto w-12 h-12 text-gray-300 mb-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
        </svg>
        <p>无法解析 YAML 内容</p>
        <p className="text-xs mt-1">请使用编辑模式检查 YAML 格式</p>
      </div>
    );
  }

  const acts = parsed.acts || [];

  if (acts.length === 0) {
    return (
      <div className="p-6 text-center text-gray-500">
        <p>剧本结构为空</p>
        <pre className="mt-4 text-left text-xs bg-gray-50 p-4 rounded-lg overflow-auto max-h-96">
          {contentYaml}
        </pre>
      </div>
    );
  }

  return (
    <div className="p-4">
      {parsed.title && (
        <h2 className="text-xl font-bold text-gray-900 mb-6 pb-3 border-b border-gray-200">
          {parsed.title}
        </h2>
      )}
      {acts.map((act, i) => (
        <ActRenderer key={i} act={act} index={i} />
      ))}
    </div>
  );
}
