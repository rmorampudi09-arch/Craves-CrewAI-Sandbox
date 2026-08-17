import {
  Home,
  ShieldCheck,
  Carrot,
  ChefHat,
  Bike,
  Wallet,
  Users,
  Truck,
  Building2,
} from "lucide-react";
import stepChoose from "@/assets/images/step-choose.jpg";
import stepOrder from "@/assets/images/step-order.jpg";
import stepCook from "@/assets/images/step-cook.jpg";
import stepDeliver from "@/assets/images/step-deliver.jpg";
import { assetUrl } from "@/lib/asset-url";

export const navLinks = ["Home", "Explore", "Home Chefs", "Categories", "About Us", "Contact"];

export const howItWorksSteps = [
  {
    img: assetUrl(stepChoose),
    title: "1. Choose a Dish",
    desc: "Explore a variety of homemade dishes from nearby chefs.",
  },
  { img: assetUrl(stepOrder), title: "2. Place Your Order", desc: "Place your order securely and easily." },
  {
    img: assetUrl(stepCook),
    title: "3. Chef Cooks with Love",
    desc: "Our home chefs prepare your meal with care and fresh ingredients.",
  },
  {
    img: assetUrl(stepDeliver),
    title: "4. We Deliver to You",
    desc: "Fast and safe delivery right to your doorstep.",
  },
];

export const whyChooseFeatures = [
  { icon: Home, title: "100% Homemade", desc: "Food made at home with love & care." },
  {
    icon: ShieldCheck,
    title: "Hygienic Kitchens",
    desc: "All chefs follow strict hygiene standards.",
  },
  { icon: Carrot, title: "Fresh Ingredients", desc: "Only fresh & quality ingredients used." },
  { icon: ChefHat, title: "Trusted Home Chefs", desc: "Verified chefs you can rely on." },
  { icon: Bike, title: "Fast Delivery", desc: "Quick and safe delivery to your doorstep." },
  { icon: Wallet, title: "Affordable Prices", desc: "Great food at pocket-friendly prices." },
];

export const customerReviews = [
  {
    name: "Priya Sharma",
    text: "The food is amazing and feels just like home. Craves is my go-to app now!",
  },
  {
    name: "Neha Verma",
    text: "Great platform for home chefs like me to earn and grow. Thank you Craves!",
  },
  { name: "Rahul Mehta", text: "On-time delivery and super tasty food. Highly recommended!" },
];

export const platformStats = [
  { icon: Users, value: "10K+", label: "Happy Customers" },
  { icon: ChefHat, value: "500+", label: "Home Chefs" },
  { icon: Truck, value: "50K+", label: "Orders Delivered" },
  { icon: Building2, value: "25+", label: "Cities" },
];
