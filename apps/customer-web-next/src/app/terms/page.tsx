import { PolicyList, PolicySection, PublicPolicyPage } from "@/components/legal/PublicPolicyPage";

export const metadata = {
  title: "Terms of Service | Craves",
  description: "Terms governing customer use of the Craves homemade food marketplace.",
};

export default function TermsPage() {
  return (
    <PublicPolicyPage
      eyebrow="Terms of service"
      title="Terms for using Craves."
      intro="Effective 15 August 2026. These terms apply to customer use of the Craves marketplace in Hyderabad, India. They should be read together with the Refunds & Cancellations and Privacy pages."
    >
      <PolicySection title="1. What Craves provides">
        <p>
          Craves is a marketplace where customers can discover approved home chefs, view available dishes and meal plans, place orders, pay online, and track fulfilment. Craves coordinates the marketplace technology, payment workflow, and supported delivery workflow for eligible orders.
        </p>
      </PolicySection>

      <PolicySection title="2. Accounts and secure access">
        <PolicyList>
          <li>You must provide accurate account and order information.</li>
          <li>Phone possession is verified through Firebase Authentication. Craves then applies its own account roles and secure session controls.</li>
          <li>Do not share OTPs, passwords, session credentials, UPI PINs, card security codes, or other authentication secrets.</li>
          <li>You are responsible for activity performed through your account unless you report suspected unauthorised access to Craves support.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection title="3. Menus, prices and checkout">
        <PolicyList>
          <li>Dish and meal-plan availability can change as chefs update menus, capacity, and availability.</li>
          <li>Customer-facing prices and the final checkout amount are displayed in Indian rupees (INR).</li>
          <li>The amount shown in the final checkout is the amount Craves sends to the payment provider for that checkout.</li>
          <li>One checkout may contain chef-specific sub-orders. Each chef-specific order has its own fulfilment lifecycle after payment.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection title="4. Payments">
        <PolicyList>
          <li>Online payments are processed through Razorpay using its hosted payment experience.</li>
          <li>Craves does not ask customers to send card numbers, CVVs, UPI PINs, or banking passwords to Craves support.</li>
          <li>A browser success screen alone does not establish final payment status. Craves relies on verified server-side payment-provider status and signed provider events.</li>
          <li>Cash on delivery is not part of the current Craves launch payment flow.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection title="5. Chef acceptance and fulfilment">
        <PolicyList>
          <li>After verified payment, an order may wait for the assigned chef to accept it.</li>
          <li>Delivery is scheduled only after the relevant chef-specific order is accepted and based on its preparation timing.</li>
          <li>If a chef-specific order is declined or reaches the configured chef-acceptance timeout, the affected order follows the refund rules published on the Refunds & Cancellations page.</li>
          <li>Craves does not automatically substitute a different chef for a rejected or timed-out chef-specific order.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection title="6. Cancellations and refunds">
        <p>
          Refund eligibility and processing for the currently implemented order cases are described on the{" "}
          <a className="font-semibold text-[#F62E18] underline" href="/refunds-cancellations">
            Refunds & Cancellations
          </a>{" "}
          page. For an unlisted case, contact support rather than assuming that an automatic refund is available.
        </p>
      </PolicySection>

      <PolicySection title="7. Acceptable use">
        <PolicyList>
          <li>Do not misuse Craves to commit fraud, abuse a chef or customer, interfere with service operation, or attempt unauthorised access.</li>
          <li>Do not submit payment, account, delivery, or identity information that you are not authorised to use.</li>
          <li>Craves may restrict access when required to protect customers, chefs, the platform, or payment and delivery systems.</li>
        </PolicyList>
      </PolicySection>

      <PolicySection title="8. Support and updates">
        <p>
          Questions about an order or these terms can be sent to{" "}
          <a className="font-semibold text-[#F62E18] underline" href="mailto:support@craves.in">support@craves.in</a>.
          Material changes to these customer-facing terms will be published on this page with an updated effective date.
        </p>
      </PolicySection>
    </PublicPolicyPage>
  );
}
