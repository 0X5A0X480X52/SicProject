"""Generate an idempotent patch for the exact UTF-8-as-MySQL-latin1 seed corruption."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / 'infra/mysql/patches/20261011_01_repair_iam_seed_text.sql'


def statement(table, key, code, column, value):
    # Both reference and corrupted pattern are byte literals; shell/editor encoding cannot change them.
    literal = '0x' + value.encode('utf-8').hex()
    expected = f'CONVERT({literal} USING utf8mb4)'
    corrupt = f'CONVERT(CONVERT({literal} USING latin1) USING utf8mb4)'
    condition = f"{key}='{code}'" + (" AND id=1" if table == 'department' else '')
    return (f'UPDATE {table} SET {column}={expected}\n'
            f' WHERE {condition} AND BINARY {column}=BINARY {corrupt};')


def repair_fields():
    seed = (ROOT / 'infra/mysql/seed/iam.sql').read_text(encoding='utf-8')
    department = re.search(r"VALUES \(1, '([^']+)', 'SYSTEM'\)", seed).group(1)
    roles = re.findall(r"\('([^']+)', '([^']+)', '([^']+)', TRUE\)", seed)
    permissions = re.findall(r"\('([^']+)', '([^']+)'\)", seed)
    if len(roles) != 6 or len(permissions) != 23:
        raise ValueError('Review seed vocabulary before changing this repair patch')
    fields = [('department', 'code', 'SYSTEM', 'name', department)]
    for code, name, description in roles:
        fields.extend([('app_role', 'code', code, 'name', name),
                       ('app_role', 'code', code, 'description', description)])
    fields.extend(('app_permission', 'code', code, 'name', name) for code, name in permissions)
    return fields


def generate():
    updates = [statement(*field) for field in repair_fields()]
    return ('-- Reference: fc8c604 IAM V2 seed; original labels unchanged in the release seed.\n'
            '-- Apply only to sic_iam after backup. Exact corrupt byte patterns only; custom labels remain.\n'
            '-- Does not change IDs, codes, users, role assignments or role permissions. Safe to repeat.\n'
            'SET NAMES utf8mb4;\nSTART TRANSACTION;\n\n' + '\n\n'.join(updates) + '\n\nCOMMIT;\n')


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    text = generate()
    if args.check:
        if PATCH.read_text(encoding='utf-8') != text:
            raise ValueError('IAM repair patch differs from its audited generator')
    else:
        PATCH.write_text(text, encoding='utf-8', newline='\n')
    print('36 exact seed text fields generated/checked; database application is a separate step.')
