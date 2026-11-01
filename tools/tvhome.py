#!/usr/bin/env python3
"""TV Home Studio computer installer. Python 3.9+, adb; no third-party modules.

Every device call uses --serial. Cleanup is explicit, allowlisted and reversible.
Local restore files contain a device fingerprint; do not publish them.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time

PACKAGE = 'com.gathin.tvhome'
PROVIDER = 'content://com.gathin.tvhome.studio'
CLEAN = {
    'com.amazon.wirelessmetrics.service': 'Wireless usage metrics',
    'com.amazon.tv.fw.metrics': 'TV usage metrics',
    'com.amazon.dp.logger': 'Diagnostic logger',
    'com.amazon.hybridadidservice': 'Advertising identifier',
    'com.amazon.neo.minerva': 'Minerva metrics',
    'com.amazon.neo.minerva.logcat': 'Minerva log collector',
    'com.amazon.perfcollection': 'Performance collection',
    'com.amazon.perfc': 'Performance reporting',
    'com.amazon.kso.blackbird': 'Promotional content',
    'com.amazon.sneakpeek': 'Content previews',
    'com.amazon.tv.easyupgrade': 'Upgrade promotion',
    'com.amazon.shoptv.client': 'Amazon shopping',
    'com.amazon.shoptv.firetv.client': 'Fire TV shopping',
}
STATES = {0: 'default-state', 1: 'enable', 2: 'disable',
          3: 'disable-user', 4: 'disable-until-used'}


class TV:
    def __init__(self, adb, serial):
        self.adb, self.serial = adb, serial

    def run(self, *args, timeout=60):
        return subprocess.check_output(
            [self.adb, '-s', self.serial, *args],
            text=True, stderr=subprocess.STDOUT, timeout=timeout).strip()

    def shell(self, *args):
        # ADB sends shell commands to Android's shell. Quote each argument there.
        script = shlex.join(args) + "; printf '\n__TVHOME_RC:%s\n' $?"
        output = self.run('shell', script)
        body, marker, code = output.rpartition('__TVHOME_RC:')
        if not marker or code.strip() != '0':
            raise RuntimeError(body.strip() or output)
        return body.strip()

    def call(self, method, arg=None):
        args = ['content', 'call', '--uri', PROVIDER, '--method', method]
        if arg is not None:
            args += ['--arg', arg]
        output = self.shell(*args)
        match = re.search(r'payload=([A-Za-z0-9_=-]+)', output)
        if not match:
            raise RuntimeError(output or 'Launcher provider unavailable; install first.')
        return json.loads(base64.urlsafe_b64decode(match[1]))

    def preflight(self):
        if self.run('get-state') != 'device':
            raise RuntimeError('Authorize this computer on the TV first.')
        installed = self.shell('pm', 'list', 'packages', '--user', '0')
        self.packages = {p.removeprefix('package:') for p in installed.splitlines()}
        if 'com.amazon.tv.launcher' not in self.packages:
            raise RuntimeError('This installer targets Android-based Fire TV with Amazon Home.')
        self.model = self.shell('getprop', 'ro.product.model')
        sdk = self.shell('getprop', 'ro.build.version.sdk')
        if not sdk.isdigit() or int(sdk) < 25:
            raise RuntimeError('TV Home requires Android API 25+. Fire OS 5 is unsupported.')

    def identity(self):
        serial = self.shell('getprop', 'ro.serialno')
        if not serial:
            serial = self.shell('getprop', 'ro.boot.serialno')
        if not serial:
            serial = self.shell('settings', 'get', 'secure', 'android_id')
        if not serial or serial == 'null':
            raise RuntimeError('Cannot identify this device safely.')
        return hashlib.sha256((self.model + ':' + serial).encode()).hexdigest()

    def package_state(self, package):
        dump = self.shell('dumpsys', 'package', package)
        match = re.search(r'User 0:[^\r\n]*\benabled=(\d+)', dump)
        if not match or int(match[1]) not in STATES:
            raise RuntimeError('Cannot read original state: ' + package)
        return int(match[1])

    def set_package(self, package, state):
        if package not in CLEAN or state not in STATES:
            raise ValueError('Package or state is outside the cleanup allowlist.')
        self.shell('pm', STATES[state], '--user', '0', package)
        if self.package_state(package) != state:
            raise RuntimeError('Fire OS refused the requested package state.')


def read_backup(path, identity):
    if not path.exists():
        return {'schema': 1, 'identity': identity, 'packages': {}}
    if path.stat().st_size > 100000:
        raise ValueError('Restore file is too large.')
    data = json.loads(path.read_text(encoding='utf-8'))
    if data.get('schema') != 1 or data.get('identity') != identity:
        raise ValueError('Restore file belongs to a different device or format.')
    packages = data.get('packages')
    if not isinstance(packages, dict) or any(
        p not in CLEAN or type(s) is not int or s not in STATES
        for p, s in packages.items()
    ):
        raise ValueError('Restore file contains a protected package or invalid state.')
    return data


def write_backup(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + '.tmp')
    temp.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
    os.replace(temp, path)


def choose_home(tv, choice):
    tv.shell('pm', 'grant', PACKAGE, 'android.permission.WRITE_SECURE_SETTINGS')
    if choice == 'this':
        tv.shell('pm', 'grant', PACKAGE, 'android.permission.READ_LOGS')
    tv.call('home', choice)
    if choice == 'this':
        for _ in range(20):
            health = tv.call('read')['homeHealth']
            if health['connected']:
                print('This home is connected.')
                return
            time.sleep(.5)
        raise RuntimeError('Home switching did not connect. Use repair-home; Amazon Home stays installed.')
    print('Default home selected.')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb', help='adb executable or full path')
    parser.add_argument('--serial', required=True, help='Explicit adb target, e.g. 192.168.1.50:5555')
    commands = parser.add_subparsers(dest='command', required=True)
    install = commands.add_parser('install', help='Update in place; never uninstall')
    install.add_argument('--apk', type=Path, required=True)
    install.add_argument('--home', choices=['this', 'default'], help='Omit to preserve Home choice')
    commands.add_parser('status')
    home = commands.add_parser('home')
    home.add_argument('choice', choices=['this', 'default'])
    commands.add_parser('repair-home')
    theme = commands.add_parser('theme')
    theme.add_argument('design', choices=['classic', 'cinema', 'panel'])
    commands.add_parser('list-cleanup')
    cleanup = commands.add_parser('cleanup', help='Disable only explicitly listed extras')
    cleanup.add_argument('--packages', nargs='+', required=True, choices=sorted(CLEAN))
    cleanup.add_argument('--backup', type=Path, default=Path('tvhome-restore.json'))
    restore = commands.add_parser('restore-cleanup')
    restore.add_argument('--backup', type=Path, default=Path('tvhome-restore.json'))
    args = parser.parse_args(argv)
    tv = TV(args.adb, args.serial)
    tv.preflight()
    print('Target:', tv.model)
    if args.command == 'install':
        apk = args.apk.resolve(strict=True)
        if apk.suffix.lower() != '.apk':
            raise ValueError('Select the TV-Home APK, not the phone app or a ZIP.')
        # Check package name on-device after install. Download only the published TV APK.
        print(tv.run('install', '-r', str(apk), timeout=120))
        state = tv.call('read')
        tv.shell('pm', 'grant', PACKAGE, 'android.permission.WRITE_SECURE_SETTINGS')
        tv.shell('pm', 'grant', PACKAGE, 'android.permission.READ_LOGS')
        choice = args.home or state.get('home', 'default')
        if args.home is not None or choice == 'this':
            choose_home(tv, choice)
        print('Launcher installed. Existing app data was kept.')
    elif args.command == 'status':
        state = tv.call('read')
        print(json.dumps({k: state.get(k) for k in
                         ['launcherVersion', 'active', 'home', 'homeHealth']}, indent=2))
    elif args.command == 'home':
        choose_home(tv, args.choice)
    elif args.command == 'repair-home':
        previous = tv.call('repairHome')['previousConnection']
        for _ in range(20):
            health = tv.call('read')['homeHealth']
            if health['connected'] and health['connectionEpoch'] != previous:
                print('Home switching reconnected.')
                return
            time.sleep(.5)
        raise RuntimeError('Home switching did not reconnect; inspect status on this firmware.')
    elif args.command == 'theme':
        state = tv.call('read')
        design = 'compact' if args.design == 'panel' else args.design
        if not any(t['id'] == design for t in state['themes']):
            state['themes'].append({'id': design, 'layout': design,
                                    'name': args.design.title(), 'wallpapers': []})
        state['active'] = design
        tv.call('save', base64.urlsafe_b64encode(json.dumps(state).encode()).decode())
        print(args.design.title() + ' selected.')
    elif args.command == 'list-cleanup':
        for package, label in CLEAN.items():
            if package in tv.packages:
                print(f'{package}  state={tv.package_state(package)}  {label}')
        print('States: 0 default, 1 enabled, 2 disabled, 3 disabled-user, 4 disabled-until-used.')
    else:
        path = args.backup.resolve()
        if args.command == 'restore-cleanup' and not path.exists():
            raise FileNotFoundError('No restore file: ' + str(path))
        backup = read_backup(path, tv.identity())
        requested = args.packages if args.command == 'cleanup' else list(backup['packages'])
        failures = []
        for package in requested:
            if package not in tv.packages:
                failures.append(package + ': not installed')
                continue
            if args.command == 'cleanup' and package not in backup['packages']:
                backup['packages'][package] = tv.package_state(package)
                # Persist the original state BEFORE attempting a mutation.
                write_backup(path, backup)
            state = 3 if args.command == 'cleanup' else backup['packages'][package]
            try:
                tv.set_package(package, state)
                print(package + ': ' + STATES[state])
            except RuntimeError as error:
                failures.append(package + ': ' + str(error))
        print('Restore file:', path)
        if failures:
            raise RuntimeError('\n'.join(failures))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError, KeyError) as error:
        print('Error:', error, file=sys.stderr)
        sys.exit(1)
