load(
    "@com_googlesource_gerrit_bazlets//tools:junit.bzl",
    "junit_tests",
)

def tests(tests):
  for src in tests:
    name = src[len('tst/'):len(src)-len('.java')].replace('/', '_')
    labels = []
    if name.startswith('org_eclipse_jgit_'):
      l = name[len('org.eclipse.jgit_'):]
      if l.startswith('internal_storage_'):
        l = l[len('internal.storage_'):]
      i = l.find('_')
      if i > 0:
        labels.append(l[:i])
      else:
        labels.append(i)
    if 'lib' not in labels:
      labels.append('lib')

    additional_deps = []
    if src.endswith("RootLocaleTest.java"):
      additional_deps = [
        '//org.eclipse.jgit.pgm:pgm',
        '//org.eclipse.jgit.ui:ui',
      ]

    junit_tests(
      name = name,
      tags = labels,
      srcs = [src],
      deps = additional_deps + [
        ':helpers',
        ':tst_rsrc',
        '//org.eclipse.jgit:jgit',
        '//org.eclipse.jgit.junit:junit',
        '//org.eclipse.jgit.lfs:jgit-lfs',
        '@hamcrest_core//jar',
        '@hamcrest_library//jar',
        '@javaewah//jar',
        '@junit//jar',
        '@log_api//jar',
        '@slf4j_simple//jar',
      ],
      jvm_flags = ["-Xmx256m", "-Dfile.encoding=UTF-8"],
    )
