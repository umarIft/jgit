prebuilt_jar(
  name = 'jgit',
  binary_jar = '//org.eclipse.jgit:jgit',
  visibility = ['PUBLIC'],
)

genrule(
  name = 'jgit_src',
  cmd = 'ln -s $(location //org.eclipse.jgit:jgit_src) $OUT',
  out = 'jgit_src.zip',
  visibility = ['PUBLIC'],
)

java_library(
  name = 'jgit-servlet',
  exported_deps = [
    ':jgit',
    '//org.eclipse.jgit.http.server:jgit-servlet'
  ],
  visibility = ['PUBLIC'],
)

java_library(
  name = 'jgit-archive',
  exported_deps = [
    ':jgit',
    '//org.eclipse.jgit.archive:jgit-archive'
  ],
  visibility = ['PUBLIC'],
)

java_library(
  name = 'jgit-lfs',
  exported_deps = [
    ':jgit',
    '//org.eclipse.jgit.lfs:jgit-lfs'
  ],
  visibility = ['PUBLIC'],
)

genrule(
  name = 'jgit-lfs_src',
  cmd = 'ln -s $(location //org.eclipse.jgit.lfs:jgit-lfs_src) $OUT',
  out = 'jgit-lfs_src.zip',
  visibility = ['PUBLIC'],
)

java_library(
  name = 'jgit-lfs-server',
  exported_deps = [
    ':jgit',
    ':jgit-lfs',
    '//org.eclipse.jgit.lfs.server:jgit-lfs-server'
  ],
  visibility = ['PUBLIC'],
)

genrule(
  name = 'jgit-lfs-server_src',
  cmd = 'ln -s $(location //org.eclipse.jgit.lfs.server:jgit-lfs-server_src) $OUT',
  out = 'jgit-lfs-server_src.zip',
  visibility = ['PUBLIC'],
)

prebuilt_jar(
  name = 'junit',
  binary_jar = '//org.eclipse.jgit.junit:junit',
  visibility = ['PUBLIC'],
)

genrule(
  name = 'jgit_bin',
  cmd = 'ln -s $(location //org.eclipse.jgit.pgm:jgit) $OUT',
  out = 'jgit_bin',
)
