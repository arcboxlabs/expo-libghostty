{
  pkgs,
  ...
}:

{
  packages = [
    pkgs.git
    pkgs.prek
    # Example-app `pod install`; Xcode itself comes from the system.
    pkgs.cocoapods
  ];

  languages.javascript = {
    enable = true;
    # nixpkgs nodejs_24 crashes worker_threads workloads on Darwin
    # (NixOS/nixpkgs#536039); Node 26 ships the upstream V8 fix.
    package = pkgs.nodejs_26;
    corepack.enable = false;
    pnpm = {
      enable = true;
      install.enable = true;
    };
  };
  languages.typescript.enable = true;

  git-hooks = {
    package = pkgs.prek;
    hooks = {
      # eslint-config-universe runs prettier as a lint rule, so this covers formatting too.
      lint = {
        enable = true;
        name = "Lint";
        entry = "pnpm lint";
        files = "(^|/)(eslint\\.config\\.cjs|package\\.json)$|\\.(cjs|js|jsx|mjs|ts|tsx)$";
        pass_filenames = false;
      };

      typecheck = {
        enable = true;
        name = "Typecheck";
        entry = "pnpm exec tsc --noEmit";
        files = "(^|/)(package\\.json|pnpm-lock\\.yaml|tsconfig[^/]*\\.json)$|\\.(ts|tsx)$";
        pass_filenames = false;
      };

      # The GhosttyKit XCFramework must never land in git history — it is fetched
      # at install time. Reject any large binary before it gets committed.
      check-added-large-files = {
        enable = true;
        args = [ "--maxkb=512" ];
      };
    };
  };

  # The example app is metro-linked (extraNodeModules), not a pnpm workspace member.
  scripts.example.exec = "pnpm --dir example ios";
}
