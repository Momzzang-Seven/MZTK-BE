#!/usr/bin/env python3
"""
PostToolUse hook: enforce docs.shared/ARCHITECTURE.md Hexagonal Architecture rules
on .java files edited under src/main/java/.../modules/.

v1 rules (informational additionalContext, never blocks):
  1. api      → must NOT import infrastructure
  2. application → must NOT import api or infrastructure
  3. domain    → must NOT import Spring/JPA/QueryDSL/Hibernate or other layers
                 (global.error is allowed)
  4. application/service → must NOT import classes from other modules,
                           except whitelisted web3.shared subpackages

Output (only if violations found):
  hookSpecificOutput.additionalContext = human-readable summary

All exceptions are swallowed silently — a hook crash must never block the user.
"""
import sys
import json
import os
import re

PROJECT_ROOT_MARKER = "src/main/java/momzzangseven/mztkbe/modules/"
PACKAGE_PREFIX = "momzzangseven.mztkbe.modules."

DOMAIN_FORBIDDEN_PREFIXES = (
    "org.springframework.",
    "jakarta.persistence.",
    "javax.persistence.",
    "com.querydsl.",
    "org.hibernate.",
)

DOMAIN_ALLOWED_GLOBAL = "momzzangseven.mztkbe.global.error."

LAYER_KEYS = ("api", "application", "domain", "infrastructure")

# Suffixes that a sibling sub-module (within the same top-level family) may legitimately
# import from another sibling. Matches the hexagonal call-via-port convention plus
# read-only cross-sub-module type sharing observed in admin/marketplace/web3.
SIBLING_WHITELIST_SUFFIXES = (
    ".application.port.in.",
    ".application.dto.",
    ".application.util.",
    ".domain.vo.",
    ".domain.model.",
    ".domain.crypto.",
)

PKG_RE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.MULTILINE)
IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.*]+)\s*;", re.MULTILINE)


def _resolve_path(fp, cwd):
    if fp.startswith(cwd):
        fp = fp[len(cwd):].lstrip("/")
    return fp


def _split_module(parts):
    """Return (module, layer_parts). Treat first segment as a sub-module discriminator
    when the next segment is not a known layer keyword (covers web3/admin/marketplace).
    """
    if not parts:
        return None, []
    if len(parts) >= 2 and parts[1] not in LAYER_KEYS:
        return f"{parts[0]}.{parts[1]}", parts[2:]
    return parts[0], parts[1:]


def _family(module):
    return module.split(".", 1)[0] if module else None


def _layer_of(pkg):
    """Return ('api'|'application'|'application.service'|'domain'|'infrastructure'|None, module_name)."""
    if not pkg.startswith(PACKAGE_PREFIX):
        return None, None
    parts = pkg[len(PACKAGE_PREFIX):].split(".")
    module, layer_parts = _split_module(parts)
    if module is None:
        return None, None
    if not layer_parts:
        return None, module

    head = layer_parts[0]
    if head == "application" and len(layer_parts) >= 2 and layer_parts[1] == "service":
        return "application.service", module
    if head in LAYER_KEYS:
        return head, module
    return None, module


def _is_module_import(imp):
    return imp.startswith(PACKAGE_PREFIX)


def _import_module(imp):
    if not _is_module_import(imp):
        return None
    parts = imp[len(PACKAGE_PREFIX):].split(".")
    module, _ = _split_module(parts)
    return module


def _is_module_layer_import(imp, layer):
    """imp 가 modules.<m>[.<sub>].<layer>.* 형태인지."""
    if not _is_module_import(imp):
        return False
    parts = imp[len(PACKAGE_PREFIX):].split(".")
    _, layer_parts = _split_module(parts)
    return bool(layer_parts) and layer_parts[0] == layer


def _check_api(imports, module):
    out = []
    for imp in imports:
        if _is_module_layer_import(imp, "infrastructure"):
            out.append(("rule1", imp))
    return out


def _check_application(imports, module, is_service):
    out = []
    for imp in imports:
        if _is_module_layer_import(imp, "api"):
            out.append(("rule2", imp, "any module's api"))
        elif _is_module_layer_import(imp, "infrastructure"):
            out.append(("rule2", imp, "any module's infrastructure"))

    if is_service:
        for imp in imports:
            if not _is_module_import(imp):
                continue
            other = _import_module(imp)
            if other is None or other == module:
                continue
            same_family = _family(other) == _family(module)
            if same_family and any(s in imp for s in SIBLING_WHITELIST_SUFFIXES):
                continue
            label = (
                f"sibling sub-module '{other}'"
                if same_family
                else f"foreign module '{other}'"
            )
            out.append(("rule4", imp, label))
    return out


def _check_domain(imports, module):
    out = []
    own_other = (
        f"{PACKAGE_PREFIX}{module}.api.",
        f"{PACKAGE_PREFIX}{module}.application.",
        f"{PACKAGE_PREFIX}{module}.infrastructure.",
    )
    for imp in imports:
        if imp.startswith(DOMAIN_ALLOWED_GLOBAL):
            continue
        if any(imp.startswith(p) for p in DOMAIN_FORBIDDEN_PREFIXES):
            out.append(("rule3", imp, "framework / persistence import in domain"))
            continue
        if any(imp.startswith(p) for p in own_other):
            out.append(("rule3", imp, "cross-layer import from domain"))
            continue
        # cross-module: domain must depend on nothing from other modules
        if _is_module_import(imp):
            other = _import_module(imp)
            if other is not None and other != module:
                out.append(("rule3", imp, f"cross-module import from domain (foreign module '{other}')"))
    return out


def _format_message(path, violations):
    lines = [f"docs.shared/ARCHITECTURE.md 위반이 감지되었습니다 ({path}):"]
    rule_titles = {
        "rule1": "api → infrastructure import 금지",
        "rule2": "application → api/infrastructure import 금지",
        "rule3": "domain layer 외부 의존 금지",
        "rule4": "application.service 의 cross-module import 금지",
    }
    for v in violations:
        rule = v[0]
        imp = v[1]
        extra = f" — {v[2]}" if len(v) > 2 else ""
        lines.append(f"- {rule_titles.get(rule, rule)}: `{imp}`{extra}")
    lines.append("")
    lines.append("docs.shared/ARCHITECTURE.md 의 layer dependency rule 을 다시 확인하고 수정해 주세요.")
    return "\n".join(lines)


def main():
    try:
        data = json.load(sys.stdin)
        fp = data.get("tool_input", {}).get("file_path", "")
        if not fp.endswith(".java"):
            return
        cwd = os.getcwd()
        rel = _resolve_path(fp, cwd)
        if PROJECT_ROOT_MARKER not in rel:
            return
        if not os.path.isfile(fp):
            return

        with open(fp, "r", encoding="utf-8") as f:
            content = f.read()

        m = PKG_RE.search(content)
        if not m:
            return
        pkg = m.group(1)
        imports = IMPORT_RE.findall(content)

        layer, module = _layer_of(pkg)
        if layer is None or module is None:
            return

        violations = []
        if layer == "api":
            violations.extend(_check_api(imports, module))
        elif layer == "application" or layer == "application.service":
            violations.extend(
                _check_application(imports, module, is_service=(layer == "application.service"))
            )
        elif layer == "domain":
            violations.extend(_check_domain(imports, module))
        # infrastructure: no v1 checks (allowed wide imports)

        if not violations:
            return

        msg = _format_message(rel, violations)
        print(json.dumps({
            "hookSpecificOutput": {
                "hookEventName": "PostToolUse",
                "additionalContext": msg,
            }
        }))
    except Exception:
        # Hook must never block the user
        pass


if __name__ == "__main__":
    main()
