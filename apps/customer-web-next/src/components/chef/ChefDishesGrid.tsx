import { DishCard } from "@/components/home/DishCard";
import type { Dish } from "@/services/api/dishes";

interface ChefDishesGridProps {
  chefName: string;
  dishes: Dish[];
}

/** "Dishes by {chef}" heading + grid, reusing the same DishCard as the browse page. */
export function ChefDishesGrid({ chefName, dishes }: ChefDishesGridProps) {
  if (dishes.length === 0) return null;
  return (
    <section className="mt-6">
      <h2 className="font-display text-lg font-bold text-ink">Dishes by {chefName}</h2>
      <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3">
        {dishes.map((d) => (
          <DishCard key={d.id} dish={d} />
        ))}
      </div>
    </section>
  );
}

export default ChefDishesGrid;
