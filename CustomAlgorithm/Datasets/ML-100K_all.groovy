
ml100k = target('ml100k') {

    def dataDir = "build/data"
    def zipFile = "${dataDir}/ml-100k.zip"


    ant.mkdir(dir: dataDir)
    ant.mkdir(dir: dataDir+"/ml100k")
    ant.get(src: 'http://www.grouplens.org/system/files/ml-100k.zip',
            dest: zipFile,
            skipExisting: true)
    ant.unzip(src: zipFile, dest: dataDir+"/ml100k") {
        patternset {
            include name: 'ml-100k/*'
        }
        mapper type: 'flatten'
    }
    perform {	
		def rd = new ReduceDataset()
		rd.setMinSize(maxSize)
		def d = csvfile("${dataDir}/ml100k/u.data") {
            delimiter "\t"
            domain {
                minimum 1
                maximum 5
                precision 1.0
            }
        }	
		rd.setSource(d)
		rd.execute()
		return rd.get()
    }
}

datasets << ml100k



	