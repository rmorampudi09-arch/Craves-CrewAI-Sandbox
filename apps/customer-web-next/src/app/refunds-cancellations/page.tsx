import { PolicyList, PolicySection, PublicPolicyPage } from "@/components/legal/PublicPolicyPage";

export const metadata = {
  title: "Refunds & Cancellations | Craves",
  description: "Craves refund and cancellation policy for paid home-chef orders.",
};

export default function RefundsCancellationsPage() {
  return (
    <PublicPolicyPage
      eyebrow="Refunds & cancellations"
      title="Fair refunds when a chef cannot fulfil your paid order."
      intro="This policy describes the Craves order-refund rules currently implemented for chef rejection and chef acceptance timeout. Refund processing is tied to verified payment and order state, not a browser callback."
    >
      <PolicySection title="Chef acceptance window">
        <PolicyList>
          <li>The chef response window starts only after Craves has verified successful payment.</li>
          <li>An eligible chef-specific order enters the chef acceptance stage for up to 30 minutes.</li>
          <li>If the chef explicitly declines during that window, the affected chef-specific order is rejected and a refund is requested.</li>
          <li>If the chef does not accept within 30 minutes, the affected chef-specific order times out and a refund is requested.</li>
          <li>Craves does not create delivery booking for a chef-specific order that was declined or timed out before acceptance.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection title="Refund amount">
        <p>
          For a chef-specific order rejected because the chef declined or did not accept in time, Craves requests a refund for the full amount charged to that affected chef-specific order. The refund uses the stored order total; the refund process does not recalculate new fees or apply a new deduction.
        </p>
      </PolicySection>

      <PolicySection title="Multi-chef checkouts">
        <PolicyList>
          <li>If one chef-specific order fails while another chef-specific order is accepted, only the failed chef-specific order is refunded.</li>
          <li>An accepted chef-specific order continues normally and is not cancelled merely because another chef in the same checkout could not fulfil.</li>
          <li>If every chef-specific order in the checkout fails, the combined refunds equal the complete paid checkout amount.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection title="No automatic replacement chef">
        <p>
          Craves does not automatically transfer a rejected or timed-out order to a different chef. Different chefs may have different dishes, prices, preparation times, ingredients, packaging, and pickup locations. You may place a new order after the affected order is refunded.
        </p>
      </PolicySection>

      <PolicySection title="How the refund is processed">
        <PolicyList>
          <li>Craves sends eligible refunds to Razorpay using the standard refund flow and a duplicate-safe refund reference.</li>
          <li>Craves prevents cumulative refunds from exceeding the captured payment amount.</li>
          <li>The final credit timing depends on Razorpay, the payment method, and the customer’s bank or issuer. Craves tracks provider status instead of treating a request as completed before confirmation.</li>
          <li>If the provider reports a refund failure, the refund remains an operational exception for retry or review rather than being silently marked complete.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection title="Other cancellation or refund questions">
        <p>
          For a situation not described above, contact{" "}
          <a className="font-semibold text-[#F62E18] underline" href="mailto:support@craves.in">
            support@craves.in
          </a>{" "}
          with your order reference. This page does not promise an automatic refund for a scenario that is not listed as eligible above.
        </p>
      </PolicySection>
    </PublicPolicyPage>
  );
}
