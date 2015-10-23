ml1m = target('ml1m') {
    def dataDir = "build/data"
    def zipFile = "${dataDir}/ml-1m.zip"

    ant.mkdir(dir: dataDir)
    ant.mkdir(dir: dataDir+"/ml1m")
    ant.get(src: 'http://files.grouplens.org/datasets/movielens/ml-1m.zip',
            dest: zipFile,
            skipExisting: true)
    ant.unzip(src: zipFile, dest: dataDir+"/ml1m") {
        patternset {
            include name: 'ml-1m/*'
        }
        mapper type: 'flatten'
    }
    perform {
        csvfile("${dataDir}/ml1m/ratings.dat") {
            delimiter "::"
            domain {
                minimum 1
                maximum 5
                precision 1
            }
        }
    }
}

datasets << ml1m