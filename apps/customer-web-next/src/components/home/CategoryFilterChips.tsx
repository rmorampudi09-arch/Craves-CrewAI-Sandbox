import type { DishCategory } from "@/constants/dishCategories";

interface CategoryFilterChipsProps {
  categories: readonly DishCategory[];
  selected: DishCategory;
  onSelect: (category: DishCategory) => void;
}

export function CategoryFilterChips({
  categories,
  selected,
  onSelect,
}: CategoryFilterChipsProps) {
  if (categories.length <= 1) return null;

  return (
    <section className="mx-auto max-w-7xl px-4 pt-6 md:px-6" aria-label="Filter dishes by category">
      <div className="flex gap-2 overflow-x-auto pb-1">
        {categories.map((category) => (
          <button
            key={category}
            type="button"
            onClick={() => onSelect(category)}
            aria-pressed={selected === category}
            className={`min-h-11 shrink-0 rounded-full border px-4 text-sm font-semibold transition-colors ${
              selected === category
                ? "border-primary bg-primary text-white"
                : "border-border bg-white text-ink hover:border-primary"
            }`}
          >
            {category}
          </button>
        ))}
      </div>
    </section>
  );
}

export default CategoryFilterChips;
