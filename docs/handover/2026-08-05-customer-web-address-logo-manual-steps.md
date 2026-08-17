# Manual Steps — Customer Web Address and Logo Fix

After PR checks pass and the change is merged:

1. Open the existing Azure DevOps customer-web delivery pipeline.
2. Select branch `main`.
3. Set `confirmReplaceCurrentCustomerWeb=true`.
4. Keep `cashfreeMode=sandbox`.
5. Run the pipeline once.

Do not rerun the customer-address APIM pipeline. Its existing operations are already configured.

No Azure Portal resource creation, secret change, DNS action, Firebase Console action, Cashfree action or database command is required.
