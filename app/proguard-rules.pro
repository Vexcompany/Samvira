# SAMVIRA ProGuard/R8 rules.
#
# Minification is disabled for Milestone 0.1. These rules are kept as a
# foundation for the release configuration. When minification is enabled,
# add keep rules for reflection-based frameworks (if any are introduced) here.

# Keep line numbers for readable stack traces in crash reporting.
-keepattributes SourceFile,LineNumberTable
