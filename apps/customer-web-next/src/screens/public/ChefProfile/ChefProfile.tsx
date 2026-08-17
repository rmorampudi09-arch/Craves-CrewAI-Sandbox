import { getRouteApi, useNavigate, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { PersistentCustomerServiceNav } from "@/components/navigation/PersistentCustomerServiceNav";
import {
  getChef,
  getDishesByChef,
  type Chef,
} from "@/services/api/chefs";
import { discoverDishes } from "@/services/api/dishes";
import {
  loadSelectedAddress,
  loadSession,
} from "@/services/auth/cravesAuth";
import { ChefProfileHeader } from "@/components/chef/ChefProfileHeader";
import { ChefProfileHero } from "@/components/chef/ChefProfileHero";
import { ChefStatsRow } from "@/components/chef/ChefStatsRow";
import { ChefAboutSection } from "@/components/chef/ChefAboutSection";
import { ChefDishesGrid } from "@/components/chef/ChefDishesGrid";
import { CustomerReviewsSection } from "@/components/order/CustomerReviewsSection";

export const routeMeta = {
  head: ({ params }: { params: { id: string } }) => {
    const chef = getChef(params.id);
    return {
      meta: [
        { title: chef ? `${chef.name} – Craves` : "Chef – Craves" },
        {
          name: "description",
          content: chef
            ? `${chef.name} · Active home kitchen on Craves.`
            : "Chef profile on Craves.",
        },
        { name: "robots", content: "noindex" },
      ],
    };
  },
};

const routeApi = getRouteApi("/chef/$id");

function ChefProfilePage() {
  const { id } = routeApi.useParams();
  const navigate = useNavigate();
  const [chef, setChef] = useState<Chef | undefined>(() => getChef(id));
  const [loading, setLoading] = useState(!chef);
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;
    setLoading(true);
    setMessage("");
    void (async () => {
      const session = await loadSession();
      if (!session) {
        navigate({ to: "/" });
        return;
      }
      let resolved = getChef(id);
      if (!resolved) {
        const address = await loadSelectedAddress();
        if (
          typeof address?.lat === "number" &&
          typeof address.lng === "number"
        ) {
          await discoverDishes(address.lat, address.lng);
          resolved = getChef(id);
        }
      }
      if (active) setChef(resolved);
    })()
      .catch((error) => {
        if (active) {
          setMessage(
            error instanceof Error
              ? error.message
              : "Kitchen details could not be loaded.",
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id, navigate]);

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-cream px-4 text-center text-sm text-muted-foreground">
        Loading home kitchen from Craves…
      </div>
    );
  }

  if (!chef) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-cream px-4 text-center">
        <div>
          <h1 className="font-display text-2xl font-bold text-ink">
            Home kitchen not found
          </h1>
          {message && (
            <p className="mt-3 text-sm text-muted-foreground">{message}</p>
          )}
          <Link to="/home" className="btn-primary mt-6 inline-flex">
            Back to menu
          </Link>
        </div>
      </div>
    );
  }

  const dishes = getDishesByChef(chef.name);

  return (
    <div className="min-h-screen bg-cream pb-10">
      <ChefProfileHeader onBack={() => navigate({ to: "/home" })} />
      <div className="border-b border-border bg-white">
        <div className="mx-auto max-w-3xl px-4 py-3 md:px-6">
          <PersistentCustomerServiceNav />
        </div>
      </div>
      <main className="mx-auto max-w-3xl px-4 pt-6 md:px-6">
        <ChefProfileHero chef={chef} />
        <ChefStatsRow chef={chef} />
        <ChefAboutSection chef={chef} />
        <ChefDishesGrid chefName={chef.name} dishes={dishes} />
        <CustomerReviewsSection reviews={chef.reviews} />
      </main>
    </div>
  );
}

export default ChefProfilePage;
