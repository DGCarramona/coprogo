export function parseGroupName(raw: string): string | null {
  const name = raw.trim();
  return name.length > 0 && name.length <= 100 ? name : null;
}
