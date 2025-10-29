#ifndef __PLUSARG_FILE_MEM_H__
#define __PLUSARG_FILE_MEM_H__

class PlusargFileMem
{
  public:
    PlusargFileMem(const char *filename, bool writeable, uint64_t capacity_words, uint8_t data_bytes);
    ~PlusargFileMem(void);

    void do_read(uint64_t address, uint64_t *data);
    void do_write(uint64_t address, uint64_t data);

  private:
    uint64_t _capacity_bytes;
    uint64_t _filesize;
    uint8_t _data_bytes;
    bool _writeable;
    void *_memblk;

};

#endif /* __SPI_FLASH_H__ */
