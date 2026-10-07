const icons: Record<string, string> = {
  'body & health':'heart', 'clothing':'shopping-bag', 'donations':'gift',
  'groceries':'shopping-cart', 'holiday':'sun', 'home & utilities':'home', 'investment':'chart-line',
  'leisure, hobby, entertainment & digital services':'ticket', 'miscellaneous':'tags', 'money transfer':'arrows-h',
  'rückstellungen':'inbox', 'salary':'briefcase', 'shopping':'shopping-bag', 'stock market updates':'chart-line',
  'taxes':'building-columns', 'transportation':'car'
};
export function categoryIcon(name: string | null | undefined): string {
  const category = name?.trim().toLocaleLowerCase() ?? '';
  return category === 'food' ? 'fa-solid fa-utensils' : 'pi pi-' + (icons[category] ?? 'tag');
}
