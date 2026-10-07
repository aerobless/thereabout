export interface ConfigurationSearchResult {label: string; category: string; path: string; anchor: string; keywords: string;}
const connection = '/configuration/connections/';
export const configurationSearchIndex: readonly ConfigurationSearchResult[] = [
  {label: 'Finance categories', category: 'Finances', path: '/configuration/finances', anchor: 'finance-categories', keywords: 'category name add edit'},
  {label: 'Exchange rates', category: 'Finances', path: '/configuration/finances', anchor: 'finance-rates', keywords: 'currency ecb manual dated rate'},
  {label: 'Google credentials', category: 'Google Calendar', path: connection + 'google-calendar', anchor: 'google-credentials', keywords: 'oauth client id secret refresh token'},
  {label: 'Calendars and synchronization', category: 'Google Calendar', path: connection + 'google-calendar', anchor: 'google-sync', keywords: 'calendar selection users full import sync now'},
  {label: 'Google webhook setup', category: 'Google Calendar', path: connection + 'google-calendar', anchor: 'google-webhook-setup', keywords: 'automatic updates public origin callback push'},
  {label: 'Splitwise API key', category: 'Splitwise', path: connection + 'splitwise', anchor: 'splitwise-connection', keywords: 'connect test remove credentials'},
  {label: 'Group and accounts', category: 'Splitwise', path: connection + 'splitwise', anchor: 'splitwise-accounts', keywords: 'members mappings bank settlements history start date'},
  {label: 'Category mapping', category: 'Splitwise', path: connection + 'splitwise', anchor: 'splitwise-categories', keywords: 'categories mapping'},
  {label: 'Splitwise synchronization', category: 'Splitwise', path: connection + 'splitwise', anchor: 'splitwise-sync', keywords: 'automatic five minutes first import preview confirm review'},
  {label: 'OpenAI settings', category: 'OpenAI', path: connection + 'openai', anchor: 'openai-settings', keywords: 'api key model csv suggestions connection test'},
  {label: 'Telegram connection', category: 'Telegram', path: connection + 'telegram', anchor: 'telegram-settings', keywords: 'phone login code password server configuration'},
  {label: 'Telegram synchronization', category: 'Telegram', path: connection + 'telegram', anchor: 'telegram-settings', keywords: 'resync cancel disconnect messages'},
  {label: 'Data import', category: 'Location & imports', path: '/configuration/location-imports', anchor: 'data-import', keywords: 'google maps records json whatsapp chat receiver health auto export upload'},
  {label: 'Overland setup', category: 'Location & imports', path: '/configuration/location-imports', anchor: 'overland-settings', keywords: 'location tracking phone'},
  {label: 'MCP', category: 'API & access', path: '/configuration/api-access', anchor: 'mcp-settings', keywords: 'bearer key authorization tools finances'},
  {label: 'API authentication and documentation', category: 'API & access', path: '/configuration/api-access', anchor: 'api-settings', keywords: 'key swagger openapi specification'},
  {label: 'Version and project', category: 'About', path: '/configuration/about', anchor: 'version-settings', keywords: 'branch commit date version thereabout'}
];
export function searchConfiguration(query: string): ConfigurationSearchResult[] {
  const words = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return [];
  return configurationSearchIndex.filter(item => words.every(word => (item.label + ' ' + item.category + ' ' + item.keywords).toLocaleLowerCase().includes(word)));
}
