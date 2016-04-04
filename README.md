Man-Man [![Flattr this git repo](http://api.flattr.com/button/flattr-badge-large.png)](https://flattr.com/submit/auto?user_id=Antic1tizen&url=https://github.com/Adonai/Man-Man&title=Man-Man&language=Java&tags=github&category=software)
=======

Android client for accessing API and content of www.mankier.com site

Basically this means that this is a convenient tool for any Linux enthusiast familiar with
man pages. It provides a fast way to search, browse and save man pages on your android device.

Features
--------

- Supports searching for a single command
- Supports explaining command one-liners
- Supports browsing and indexing of man chapters
- Supports caching of man pages that were previously accessed
- Supports loading and viewing troff files from local man archive

In progress
-----------
- Page actions (multiple load to cache, cache purging, favorites etc.)

Updating
-----------
I consider this app as stable now,
so if you want any additional features to be included, please create an enhancement issue here


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
