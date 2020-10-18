[![Travis (.org)](https://img.shields.io/travis/Adonai/Man-Man)](https://travis-ci.org/github/Adonai/Man-Man)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/Adonai/Man-Man)](https://github.com/Adonai/Man-Man/releases)
[![F-Droid](https://img.shields.io/f-droid/v/com.adonai.manman)](https://f-droid.org/en/packages/com.adonai.manman/)
[![License GPLv3+](https://img.shields.io/badge/License-GPLv3-brightgreen.svg)](https://www.gnu.org/licenses/gpl-3.0.html)
=======

Android client for accessing API and content of www.mankier.com site

Basically this means that this is a convenient tool for any Linux enthusiast familiar with
man pages. It provides a fast way to search, browse and save man pages on your android device.

<a href="https://f-droid.org/packages/com.adonai.manman/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
        alt="Get it on F-Droid" height="60"/>
</a>
<a href="https://play.google.com/store/apps/details?id=com.adonai.manman">
    <img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
        alt="Get it on Google Play" height="60"/>
</a>

Features
--------

- Supports searching for a single command
- Supports explaining command one-liners
- Supports browsing and indexing of man chapters
- Supports caching of man pages that were previously accessed
- Supports loading and viewing troff files from local man archive

Donate
--------

[<img alt="Patreon Page" src="https://s3.amazonaws.com/patreon_public_assets/toolbox/patreon.png" height="40"/>](https://www.patreon.com/kanedias)


In progress
-----------
- Page actions (multiple load to cache, cache purging, favorites etc.)

Updating
-----------
I consider this app as stable now,
so if you want any additional features to be included, please create an enhancement issue here

Local archive
-------------

You may wonder what's the hosting from which local archive is taken. In fact, it's same old Github. 
Content of the archive is assembled just by zipping manpage directory of Archlinux at nearly 2016-08-16. 
The archive in question is accessible from [here](https://github.com/Adonai/Man-Man/releases/download/1.6.0/manpages.zip),
application just downloads it if requested.


P.S. Regarding offline mode
-----------
While current implementation lacks some important features, 
I'd like to clarify on how to properly organize directory structure for this to work.
Basically this is identical to how Linux distributions place their man files in `/usr/share/man` directory.
The structure of directories you should point a scanner too should look like this:
```
├── man1
│   ├── chage.1.gz
│   ├── checkXML.1.gz
│   ├── checkXML5.1.gz
│   ├── expiry.1.gz
│   ├── gpasswd.1.gz
│   ├── swappo.1.gz
│   └── xml2pot.1.gz
├── man3
│   ├── getspnam.3.gz
│   └── shadow.3.gz
├── man5
│   ├── faillog.5.gz
│   └── suauth.5.gz
├── man6
│   └── kpat.6.gz
├── man7
│   ├── qt5options.7.gz
│   └── qtoptions.7.gz
└── man8
    ├── chgpasswd.8.gz
    ├── userdel.8.gz
    └── usermod.8.gz
```

Man Man will recursively scan all the nested dirs and find appropriate (i.e. gzipped TROFF format) files

License
-------

    Copyright (C) 2016-2020 Oleg `Kanedias` Chernovskiy

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
