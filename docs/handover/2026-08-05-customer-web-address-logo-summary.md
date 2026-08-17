# Summary

The customer web address failure was caused by strict parsing of historical nullable address rows, not by an absent address backend. The logo failure was caused by patching a legacy wrapper and serving an embedded PNG through an SVG wrapper. The source fix preserves historical rows for repair, blocks incomplete rows from checkout, exposes safe diagnostics, and serves the approved image as a real PNG generated deterministically before build.
